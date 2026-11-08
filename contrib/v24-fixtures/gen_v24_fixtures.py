#!/usr/bin/env python3
"""Generate dashj's Dash Core v24 test vectors from a regtest node.

Builds on Dash Core's functional test framework. It activates v24 on regtest, registers a version 3
Evo node and a version 3 regular masternode, updates them with v3 ProUpServTx / ProUpRegTx, and writes:

  - mnlistdiff-regtest-0-<height>-70235-after24.HF.dat, a raw mnlistdiff requested at protocol 70235
  - v24-vectors.json, the raw transactions with Core's decoding of them, a v4 coinbase,
    and hand-built asset unlock / ProDisTx transactions with the txids Core computes for them

Usage, from a built Dash Core v24 tree:

  DASH_SRC=~/dash PYTHONPATH=contrib/v24-fixtures V24_OUTDIR=/tmp/v24 \
      python3 contrib/v24-fixtures/gen_v24_fixtures.py --configfile=$DASH_SRC/test/config.ini

PYTHONPATH picks up the dash_hash stub next to this file; X11 is never needed here. The tests read a
trimmed copy of v24-vectors.json from core/src/test/resources/org/bitcoinj/evolution.
"""
import json
import os
import sys

DASH = os.path.expanduser(os.environ["DASH_SRC"])
sys.path.insert(0, os.path.join(DASH, "test", "functional"))

from test_framework import p2p as p2p_module  # noqa: E402
from test_framework.messages import msg_getmnlistd  # noqa: E402
from test_framework.p2p import P2PInterface, MESSAGEMAP  # noqa: E402
from test_framework.script import hash160  # noqa: E402
from test_framework.test_framework import BitcoinTestFramework, MasternodeInfo, p2p_port  # noqa: E402
from test_framework.util import assert_equal, softfork_active  # noqa: E402

V24_ACTIVATION_THRESHOLD = 100
DASHJ_PROTOCOL = 70235
OUTDIR = os.environ.get("V24_OUTDIR", "/tmp")


class msg_raw_mnlistdiff:
    msgtype = b"mnlistdiff"

    def __init__(self):
        self.raw = b""

    def deserialize(self, f):
        self.raw = f.read()

    def serialize(self):
        return self.raw

    def __repr__(self):
        return "msg_raw_mnlistdiff(%d bytes)" % len(self.raw)


MESSAGEMAP[b"mnlistdiff"] = msg_raw_mnlistdiff


class RawDiffPeer(P2PInterface):
    def __init__(self):
        super().__init__()
        self.last_raw = None

    def on_mnlistdiff(self, message):
        self.last_raw = message.raw


class V24Fixtures(BitcoinTestFramework):
    def add_options(self, parser):
        self.add_wallet_options(parser)

    def set_test_params(self):
        self.num_nodes = 1
        self.setup_clean_chain = False
        self.extra_args = [[
            "-dip3params=2:2", f"-vbparams=v24:{self.mocktime}:999999999999:{V24_ACTIVATION_THRESHOLD}:10:8:6:5:0",
            "-txindex=1", "-testactivationheight=v20@120",
        ]]

    def skip_test_if_missing_module(self):
        self.skip_if_no_wallet()

    def bury(self, txid):
        node = self.nodes[0]
        self.bump_mocktime(10 * 60 + 1)
        tip = self.generate(node, 1, sync_fun=self.no_op)[0]
        assert_equal(node.getrawtransaction(txid, 1, tip)['confirmations'], 1)

    def make_mn(self, evo):
        node = self.nodes[0]
        mn = MasternodeInfo(evo=evo, legacy=False)
        mn.generate_addresses(node)
        mn.set_node(0)
        mn.set_params(nodePort=p2p_port(0) + (1 if evo else 2))
        while node.getbalance() < mn.get_collateral_value() + 10:
            self.bump_mocktime(1)
            self.generate(node, 10, sync_fun=self.no_op)
        txid = node.sendmany("", {mn.collateral_address: mn.get_collateral_value(), mn.fundsAddr: 1})
        self.bury(txid)
        mn.set_params(collateral_txid=txid, collateral_vout=mn.get_collateral_vout(node, txid))
        return mn

    def run_test(self):
        node = self.nodes[0]
        out = {}

        while not softfork_active(node, "v24"):
            self.bump_mocktime(50)
            self.generate(node, 50, sync_fun=self.no_op)
        self.log.info("v24 active at height %d", node.getblockcount())

        # A v3 Evo node with all three address purposes
        evo = self.make_mn(True)
        platform_node_id = hash160(b"dashj-v24").hex()
        evo_protx = evo.register(node, submit=True, addrs_core_p2p=[f"127.0.0.1:{evo.nodePort}"], operator_reward=0,
                                 platform_node_id=platform_node_id, addrs_platform_p2p=[f"127.0.0.1:{evo.nodePort + 100}"],
                                 addrs_platform_https=[f"127.0.0.1:{evo.nodePort + 200}"])
        evo.set_params(proTxHash=evo_protx, operator_reward=0)
        self.bury(evo_protx)

        # A v3 regular masternode with two payouts
        reg = self.make_mn(False)
        payout_a = node.getnewaddress()
        payout_b = node.getnewaddress()
        reg_protx = reg.register(node, submit=True, addrs_core_p2p=[f"127.0.0.1:{reg.nodePort}"], operator_reward=0,
                                 payouts=[{"address": payout_a, "reward": 7000}, {"address": payout_b, "reward": 3000}])
        reg.set_params(proTxHash=reg_protx, operator_reward=0)
        self.bury(reg_protx)

        # v3 ProUpServTx for the Evo node, v3 ProUpRegTx for the regular node
        upserv = evo.update_service(node, submit=True, addrs_core_p2p=[f"127.0.0.1:{evo.nodePort}"],
                                    platform_node_id=platform_node_id,
                                    addrs_platform_p2p=[f"127.0.0.1:{evo.nodePort + 101}"],
                                    addrs_platform_https=[f"127.0.0.1:{evo.nodePort + 201}"])
        self.bury(upserv)
        self.bury(node.sendtoaddress(reg.fundsAddr, 1))
        upreg = reg.update_registrar(node, submit=True, payouts=[{"address": payout_b, "reward": 10000}],
                                     fundsAddr=reg.fundsAddr)
        self.bury(upreg)

        for name, txid in [("proregtx_v3_evo", evo_protx), ("proregtx_v3_regular", reg_protx),
                           ("proupservtx_v3_evo", upserv), ("proupregtx_v3", upreg)]:
            out[name] = {"txid": txid, "hex": node.getrawtransaction(txid, 0), "json": node.getrawtransaction(txid, 1)}

        tip_hash = node.getbestblockhash()
        block = node.getblock(tip_hash, 2)
        cb = block["tx"][0]
        out["cbtx_v4"] = {"txid": cb["txid"], "hex": cb["hex"], "cbTx": cb.get("cbTx"), "height": block["height"],
                          "blockhash": tip_hash}

        # Asset unlocks and a ProDisTx, decoded by Core to get its txid rules
        def special_tx(tx_type, payload):
            tx = (3 | (tx_type << 16)).to_bytes(4, "little")
            tx += b"\x00"                                              # no inputs
            tx += b"\x01" + (12345678).to_bytes(8, "little")           # one output
            tx += b"\x19\x76\xa9\x14" + bytes(range(20)) + b"\x88\xac"
            tx += (0).to_bytes(4, "little")                             # lock time
            tx += bytes([len(payload)]) + payload if len(payload) < 0xfd else b"\xfd" + len(payload).to_bytes(2, "little") + payload
            return tx.hex()

        def unlock_payload(version, sig_byte):
            p = bytes([version]) + (101).to_bytes(8, "little") + (2000).to_bytes(4, "little")
            p += (250).to_bytes(4, "little") + bytes([0x11] * 32) + bytes([sig_byte] * 96)
            return p

        vectors = {}
        for name, payload in [("assetunlock_v2_a", unlock_payload(2, 0xa1)), ("assetunlock_v2_b", unlock_payload(2, 0xb2)),
                              ("assetunlock_v1", unlock_payload(1, 0xa1))]:
            h = special_tx(9, payload)
            d = node.decoderawtransaction(h)
            vectors[name] = {"hex": h, "txid": d["txid"], "instanceHash": d.get("instanceHash")}
        prodis = (1).to_bytes(2, "little") + bytes([0x22] * 32) + (0).to_bytes(2, "little") + b"\x01" + bytes([0x33] * 65)
        h = special_tx(10, prodis)
        d = node.decoderawtransaction(h)
        vectors["prodistx"] = {"hex": h, "txid": d["txid"], "type": d["type"]}
        for t in (11, 12):
            h = special_tx(t, bytes([0x44] * 40))
            try:
                d = node.decoderawtransaction(h)
                vectors[f"type{t}"] = {"hex": h, "txid": d["txid"], "type": d["type"]}
            except Exception as e:
                vectors[f"type{t}"] = {"hex": h, "error": str(e)}
        out["special"] = vectors

        # raw mnlistdiff from genesis to tip, as dashj would request it
        p2p_module.P2P_VERSION = DASHJ_PROTOCOL
        peer = node.add_p2p_connection(RawDiffPeer())
        peer.send_and_ping(msg_getmnlistd(0, int(tip_hash, 16)))
        peer.wait_until(lambda: peer.last_raw is not None)
        raw = peer.last_raw
        name = f"mnlistdiff-regtest-0-{block['height']}-{DASHJ_PROTOCOL}-after24.HF.dat"
        with open(os.path.join(OUTDIR, name), "wb") as f:
            f.write(raw)
        out["mnlistdiff"] = {"file": name, "protocol": DASHJ_PROTOCOL, "blockhash": tip_hash,
                             "rpc": node.protx("diff", 1, block["height"])}
        node.disconnect_p2ps()

        # The SML of the tip, with Core's own leaf hashes
        out["masternodes"] = node.protx("list", "registered", True)

        with open(os.path.join(OUTDIR, "v24-vectors.json"), "w") as f:
            json.dump(out, f, indent=2, default=str)
        self.log.info("wrote %s", OUTDIR)


if __name__ == "__main__":
    V24Fixtures().main()
