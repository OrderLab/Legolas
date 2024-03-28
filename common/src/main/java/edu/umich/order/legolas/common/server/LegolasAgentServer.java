package edu.umich.order.legolas.common.server;

import edu.umich.order.legolas.common.api.LegolasAgentRemote;
import edu.umich.order.legolas.common.asm.AbstractStateMachineManager;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * TODO: to be used
 */
public class LegolasAgentServer extends RmiServerBase implements LegolasAgentRemote {
    private static final Logger LOG = LoggerFactory.getLogger(LegolasAgentServer.class);

    private final AbstractStateMachineManager asmManager;

    public LegolasAgentServer(AbstractStateMachineManager asmm, Registry registry,
            boolean tryCreateReg) throws RemoteException {
        super(LegolasAgentRemote.REMOTE_NAME, LegolasAgentRemote.REMOTE_PORT,
                "LegolasAgentServer", registry, tryCreateReg);
        asmManager = asmm;
    }

    @Override
    public boolean enableASM(String className, int instanceId) throws RemoteException {
        // TODO call ASMM to enable an ASM
        return false;
    }

    @Override
    public boolean disableASM(String className, int instanceId) throws RemoteException {
        // TODO call ASMM to disable an ASM
        return false;
    }
}
