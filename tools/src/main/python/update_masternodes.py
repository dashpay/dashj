#!/usr/bin/env python3
"""
Reads an Ansible inventory file, extracts masternode and hp-masternode public IPs,
and updates the MASTERNODES and HP_MASTERNODES arrays in TestNet3Params.java.

Usage:
    python3 update_masternodes.py [inventory_file] [java_file]

Defaults:
    inventory_file: testnet.inventory (relative to script location)
    java_file:      ../../../../core/src/main/java/org/bitcoinj/params/TestNet3Params.java
"""

import re
import sys
import os


def parse_inventory(inventory_path):
    """Parse Ansible inventory and return sorted lists of (index, ip) for each node type."""
    masternode_ips = {}
    hp_masternode_ips = {}

    with open(inventory_path) as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('[') or line.startswith('#'):
                continue

            # Match host definition lines (have ansible_host= or public_ip=)
            if 'public_ip=' not in line:
                continue

            m = re.match(r'^(masternode|hp-masternode)-(\d+)\s+', line)
            if not m:
                continue

            node_type = m.group(1)
            node_num = int(m.group(2))

            ip_match = re.search(r'public_ip=(\S+)', line)
            if not ip_match:
                continue
            ip = ip_match.group(1)

            if node_type == 'masternode':
                masternode_ips[node_num] = ip
            else:
                hp_masternode_ips[node_num] = ip

    masternodes = [ip for _, ip in sorted(masternode_ips.items())]
    hp_masternodes = [ip for _, ip in sorted(hp_masternode_ips.items())]
    return masternodes, hp_masternodes


def build_java_array(ips, indent='        '):
    """Build the body of a Java String array from a list of IPs."""
    lines = []
    for ip in ips:
        lines.append(f'{indent}"{ip}",')
    # Remove trailing comma from last entry
    if lines:
        lines[-1] = lines[-1].rstrip(',')
    return '\n'.join(lines)


def replace_array(java_source, array_name, new_ips):
    """Replace the contents of a Java String array declaration."""
    # Match: <modifiers> String[] ARRAY_NAME = {\n    ...entries...\n    };
    pattern = re.compile(
        r'((?:public\s+|private\s+|protected\s+|static\s+|final\s+)*'
        r'String\s*\[\]\s*' + re.escape(array_name) + r'\s*=\s*\{)'
        r'[^}]*'
        r'(\s*\};)',
        re.DOTALL
    )

    new_body = build_java_array(new_ips)
    replacement = r'\1\n' + new_body + r'\n    \2'

    result, count = pattern.subn(replacement, java_source)
    if count == 0:
        raise ValueError(f"Could not find array '{array_name}' in Java source.")
    return result


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))

    inventory_path = sys.argv[1] if len(sys.argv) > 1 else os.path.join(script_dir, 'testnet.inventory')
    java_path = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        script_dir,
        '../../../../core/src/main/java/org/bitcoinj/params/TestNet3Params.java'
    )

    inventory_path = os.path.abspath(inventory_path)
    java_path = os.path.abspath(java_path)

    print(f"Inventory: {inventory_path}")
    print(f"Java file: {java_path}")

    masternodes, hp_masternodes = parse_inventory(inventory_path)
    print(f"Found {len(masternodes)} masternodes, {len(hp_masternodes)} hp-masternodes")

    with open(java_path) as f:
        source = f.read()

    source = replace_array(source, 'MASTERNODES', masternodes)
    source = replace_array(source, 'HP_MASTERNODES', hp_masternodes)

    with open(java_path, 'w') as f:
        f.write(source)

    print("TestNet3Params.java updated successfully.")


if __name__ == '__main__':
    main()