#DIP's in DashJ
The following DIPs are supported by dashj

DIP0001 - Initial Scaling of the Network
https://github.com/dashpay/dips/blob/master/dip-0001.md
* Increase block size from 1 MB to 2 MB
* Lower fees from 0.001 to 0.0001 per input on InstantSend transactions
* Lower fees from 0.0001 to 0.00001 per KB on regular transactions

DIP0002 - Special Transactions
https://github.com/dashpay/dips/blob/master/dip-0002-special-transactions.md
* Support Transaction version 3
    * Coinbase Transaction
    * ProRegTx
    * ProUpServTx
    * ProUpRegTx
    * ProUpRevTx
    * Quorum Commitment (limited)

DIP0003 - Deterministic Masternode (DMN) Lists
https://github.com/dashpay/dips/blob/master/dip-0003.md
* Limited support as far as required by DIP4 and InstantSend Verification.

DIP0004 - Simplified Verification of Deterministic Masternode Lists
https://github.com/dashpay/dips/blob/master/dip-0004.md
* Simplified Masternode List
* GETMNLISTDIFF message
* MNLISTDIFF message

DIP0005
* No support

DIP0006
* No support

DIP0007
* No support

DIP0008 - Chainlocks
https://github.com/dashpay/dips/blob/master/dip-0008.md
* CLSIG message
* Signature verification
* Some support for reorganizing to the chain that is locked

DIP0009 - Feature Derivation Paths
https://github.com/dashpay/dips/blob/master/dip-0009.md
* Used for Masternode Keys

DIP0010 - LLMQ InstantSend
https://github.com/dashpay/dips/blob/master/dip-0010.md
* ISLOCK message
* Singature verification

DIP0016 - Headers first Synchronization on SPV
https://github.com/dashpay/dips/blob/master/dip-0016.md

DIP0020 - Dash Opcode Updates
No Support

DIP0022 - Making InstantSend Deterministic using Quorum Cycles
https://github.com/dashpay/dips/blob/master/dip-0022.md
* ISDLOCK message
* Singature Verification

DIP0024 - Long-Living Masternode Quorum Distribution and Rotation
https://github.com/dashpay/dips/blob/master/dip-0024.md
* GETQRINFO message
* QRINFO message
* Verification of rotated quorums

DIP0025
* No support

DIP0028 - Evolution Masternodes
https://github.com/dashpay/dips/blob/master/dip-0028.md
* Supported

DIP0029 - Randomness Beacon For LLMQ Selection
https://github.com/dashpay/dips/blob/master/dip-0029.md
* Supported
