package org.bitcoinj.core;


import static com.google.common.base.Preconditions.checkState;

/**
 * Created by Hash Engineering on 6/21/2016.
 */
public abstract class AbstractManager extends Message {

    Context context;
    public AbstractManager(Context context)
    {
        super(context.getParams());
        this.context = context;
    }
    public AbstractManager(NetworkParameters params, byte [] payload, int cursor)
    {
        super(params, payload, cursor);
    }

    public abstract int calculateMessageSizeInBytes();

    public abstract void checkAndRemove();

    public abstract void clear();

    public void load(byte [] payload, int offset)
    {
        this.protocolVersion = params.getProtocolVersionNum(NetworkParameters.ProtocolVersion.CURRENT);
        this.payload = payload;
        this.cursor = this.offset = offset;
        this.length = payload.length;
        /*this.parsed = false;
        if (parseLazy) {
            parseLite();
        } else {
            parseLite();
            parse();
            parsed = true;
        }*/

        if (this.length == UNKNOWN_LENGTH)
            checkState(false, "Length field has not been set in constructor for %s after %s parse. " +
                            "Refer to Message.parseLite() for detail of required Length field contract.",
                    getClass().getSimpleName(), /*parseLazy ? "lite" :*/ "full");

        //if (parseRetain || !parsed)
        //    return;
        this.payload = null;
        parse();
    }

    public abstract AbstractManager createEmpty();

}
