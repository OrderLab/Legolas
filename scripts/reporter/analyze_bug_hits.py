#!/usr/bin/env python3

import sys
import argparse
import os
import subprocess
import shutil
import json
import csv

parser = argparse.ArgumentParser()
parser.add_argument("--system", nargs='*', choices=['zookeeper', 'hadoop',
    'kafka', 'hbase', 'cassandra'], default=['zookeeper', 'hadoop',
    'kafka', 'hbase', 'cassandra'], help="target systems to analyze")
parser.add_argument("--injection", nargs='*', choices=['ioe', 'delay'], default=['ioe', 'delay'],
        help="types of injection to analyze")
parser.add_argument("--policy", nargs='*', choices=['rr', 'newstate',
    'random', 'exhaustive', 'fate', 'metainfo'], default=['rr', 'newstate',
    'random', 'exhaustive', 'fate', 'metainfo'], help="injection policies to analyze")
parser.add_argument("--out_dir", help="Output directory")
parser.add_argument("--data_dir", required=True, help="Data directory")

POLICY_NAME_MAP = {'rr': 'round-robin', 'newstate': 'new state',
        'random': 'random', 'exhaustive': 'exhaustive',
        'fate': 'fate-destiny', 'metainfo': 'crashtuner'}

def find_bug_spec(conf_dir, injection):
    for item in os.listdir(conf_dir):
        item = os.path.join(conf_dir, item)
        if os.path.isdir(item):
            bug_spec= os.path.join(item, "%s-bugs.json" % (injection))
            if os.path.exists(bug_spec):
                return bug_spec
    return None

def copy_reporter_output(reporter_outdir, trial_summary_dir, experiment):
    try:
        src_trials = os.path.join(reporter_outdir, "trials.json")
        src_bug_hits = os.path.join(reporter_outdir, "bug-hits.json")
        dst_trials = os.path.join(trial_summary_dir, experiment + "_trials.json")
        dst_bug_hits = os.path.join(trial_summary_dir, experiment + "_bug_hits.json")
        shutil.copyfile(src_trials, dst_trials)
        shutil.copyfile(src_bug_hits, dst_bug_hits)
    except Exception as e:
        sys.stderr.write("Failed to copy reporter output: " + e + "\n")

def parse_bug_hits_json(reporter_outdir, bug_hits_result, policy):
    bug_hits_file = os.path.join(reporter_outdir, "bug-hits.json")
    if not os.path.isfile(bug_hits_file):
        sys.stderr.write("Bug hits file %s does not exist" % (bug_hits_file))
        return
    with open(bug_hits_file) as bf:
        data = json.load(bf)
        for bug in data:
            bug_data = data[bug]
            if bug not in bug_hits_result:
                bug_hits_result[bug] = {}
            bug_hits_result[bug][policy] = bug_data

def output_bug_hits_csv(bug_hits, policies, csvwriter):
    for bug, hits in bug_hits.items():
        for metric in ['first_exposure_trial', 'first_exposure_minutes', 'exposure_rate']:
            row = [bug, metric]
            data = []
            for policy in policies:
                if policy not in hits or not hits[policy]['hit_trials']:
                    data.append('N/A')
                else:
                    value = hits[policy][metric]
                    if metric == 'exposure_rate':
                        data.append("%.1f%%" % (value))
                    else:
                        if type(value) == float:
                            data.append("%.1f" % (value))
                        else:
                            data.append(value)
            row.extend(data)
            csvwriter.writerow(row)

def analyze(data_dir, systems, injections, policies, out_dir):
    script_dir = os.path.dirname(os.path.realpath(__file__))
    reporter_script = os.path.join(script_dir, "reporter.sh")
    legolas_dir = os.path.abspath(script_dir + "/../..")
    if not os.path.isfile(reporter_script) or not os.access(reporter_script, os.X_OK):
        sys.stderr.write("Reporter script %s does not exist or is not executable\n" 
                % (reporter_script))
        sys.exit(1)
    if out_dir is None:
        out_dir = data_dir
    trial_summary_dir = os.path.join(out_dir, "trial-summaries")
    os.makedirs(trial_summary_dir, exist_ok=True)

    markdown_summary = os.path.join(out_dir, "bug-hits.md")
    fmarkdown = open(markdown_summary, 'w')
    fmarkdown.write("# Bug Hits\n")
    csv_summary = os.path.join(out_dir, "bug-hits.csv")
    fcsv = open(csv_summary, 'w')
    csvwriter = csv.writer(fcsv)
    header = ["bug", "metric"]
    header.extend([POLICY_NAME_MAP[p] for p in policies])
    csvwriter.writerow(header)
    reporter_outdir = "outputReport"
    for system in systems:
        bug_hits = {}
        conf_dir=os.path.join(legolas_dir, "conf", system)
        if not os.path.isdir(conf_dir):
            sys.stderr.write("Config dir %s does not exist\n" % (conf_dir))
            continue
        for injection in injections:
            bug_spec = find_bug_spec(conf_dir, injection)
            if not bug_spec:
                sys.stderr.write("Bug spec for %s %s injections is not found\n" 
                        % (system, injection))
                continue
            reporter_spec = os.path.join(os.path.dirname(bug_spec), "reporter.json")
            if not os.path.isfile(reporter_spec):
                sys.stderr.write("Reporter spec %s is not found\n" % (reporter_spec))
                continue
            for policy in policies:
                experiment="%s-%s-%s" % (system, injection, policy)
                fmarkdown.write("### %s\n```\n" % (experiment))
                experiment_dir=os.path.join(data_dir, experiment)
                if not os.path.isdir(experiment_dir):
                    sys.stderr.write("Experiment dir %s does not exist\n" % (experiment_dir))
                    continue
                print("Running %s on %s..." % (reporter_script, experiment_dir))
                cmd="%s -e %s -b %s -s %s --enumerate -o %s" % (
                        reporter_script, experiment_dir, bug_spec, 
                        reporter_spec, reporter_outdir)
                p = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE, 
                        stderr=subprocess.STDOUT, universal_newlines=True)
                outs, _ = p.communicate()
                fmarkdown.write(outs + "\n```\n")
                print("Done")
                copy_reporter_output(reporter_outdir, trial_summary_dir, experiment)
                parse_bug_hits_json(reporter_outdir, bug_hits, policy)
        output_bug_hits_csv(bug_hits, policies, csvwriter)
    fmarkdown.close()
    fcsv.close()

if __name__ == '__main__':
    args = parser.parse_args()
    if not os.path.isdir(args.data_dir):
        sys.stderr.write("Data directory %s does not exist\n" % (args.data_dir))
        sys.exit(1)
    analyze(args.data_dir, args.system, args.injection, args.policy, args.out_dir)
