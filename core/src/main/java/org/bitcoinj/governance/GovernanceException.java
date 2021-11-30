package org.bitcoinj.governance;

public class GovernanceException extends Exception {
    String strMessage;
    public enum Type {
        /// Default value, normally indicates no exception condition occurred
        GOVERNANCE_EXCEPTION_NONE(0),
        /// Unusual condition requiring no caller action
        GOVERNANCE_EXCEPTION_WARNING(1),
        /// Requested operation cannot be performed
        GOVERNANCE_EXCEPTION_PERMANENT_ERROR(2),
        /// Requested operation not currently possible, may resubmit later
        GOVERNANCE_EXCEPTION_TEMPORARY_ERROR(3),
        /// Unexpected error (ie. should not happen unless there is a bug in the code)
        GOVERNANCE_EXCEPTION_INTERNAL_ERROR(4);

        public static final int SIZE = java.lang.Integer.SIZE;

        private int intValue;
        private static java.util.HashMap<Integer, Type> mappings;
        private static java.util.HashMap<Integer, Type> getMappings() {
            if (mappings == null) {
                synchronized (Type.class) {
                    if (mappings == null) {
                        mappings = new java.util.HashMap<Integer, Type>();
                    }
                }
            }
            return mappings;
        }

        Type(int value) {
            intValue = value;
            getMappings().put(value, this);
        }

        public int getValue() {
            return intValue;
        }

        public static Type forValue(int value) {
            return getMappings().get(value);
        }
    }


    private Type eType;

    private int nNodePenalty;

    public GovernanceException(String strMessageIn, Type eTypeIn) {
        this(strMessageIn, eTypeIn, 0);
    }
    public GovernanceException(String strMessageIn) {
        this(strMessageIn, Type.GOVERNANCE_EXCEPTION_NONE, 0);
    }
    public GovernanceException() {
        this("", Type.GOVERNANCE_EXCEPTION_NONE, 0);
    }
    public GovernanceException(String strMessageIn, Type eTypeIn, int nNodePenaltyIn) {
        super(String.format("%s:%s", eTypeIn, strMessageIn));
        this.eType = eTypeIn;
        this.nNodePenalty = nNodePenaltyIn;
        strMessage = String.format("%s:%s", eTypeIn, strMessageIn);
    }

    public void close() {
    }

    public String what() {
        return getMessage();
    }
    @Override
    public String getMessage() {
        return strMessage;
    }

    public void setException(String message, Type type)
    {
        strMessage = String.format("%s:%s", type, message);
        eType = type;
    }

    public void setException(String message, Type type, int nodePenalty)
    {
        strMessage = String.format("%s:%s", type, message);
        eType = type;
        nNodePenalty = nodePenalty;
    }

    public final Type getType() {
        return eType;
    }

    public final int getNodePenalty() {
        return nNodePenalty;
    }
}

