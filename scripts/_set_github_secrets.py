#!/usr/bin/env python3
"""One-off: encrypt 3DCreator signing values and push as GitHub repo secrets.
Uses libsodium SealedBox with the repo's Actions public key (the only scheme
GitHub's REST API accepts for repo secrets). Token is passed via argv and never
written to disk. Removes itself-safe (this script is gitignored / temp)."""
import sys, json, base64, urllib.request

TOKEN = sys.argv[1]
REPO = "lijiazhi20/3DCreator"

from nacl.public import PublicKey, SealedBox

def get_public_key():
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/secrets/public-key",
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Accept": "application/vnd.github+json",
                 "X-GitHub-Api-Version": "2022-11-28"})
    return json.load(urllib.request.urlopen(req))

def put_secret(name, value, key_id, pub_b64):
    pk = PublicKey(base64.b64decode(pub_b64))
    sealed = SealedBox(pk).encrypt(value.encode("utf-8"))
    payload = json.dumps({"encrypted_value": base64.b64encode(sealed).decode("ascii"),
                          "key_id": key_id}).encode("utf-8")
    req = urllib.request.Request(
        f"https://api.github.com/repos/{REPO}/actions/secrets/{name}",
        data=payload, method="PUT",
        headers={"Authorization": f"Bearer {TOKEN}",
                 "Accept": "application/vnd.github+json",
                 "Content-Type": "application/json",
                 "X-GitHub-Api-Version": "2022-11-28"})
    with urllib.request.urlopen(req) as resp:
        return resp.status

def main():
    k = get_public_key()
    key_id, pub_b64 = k["key_id"], k["key"]
    # Read local gitignored signing artifacts
    b64 = open("android/app/release-key.b64.txt").read().strip()
    props = {}
    for line in open("android/app/release-key.properties"):
        line = line.strip()
        if line and not line.startswith("#") and "=" in line:
            a, b = line.split("=", 1)
            props[a.strip()] = b.strip()
    secrets = {
        "KEYSTORE_BASE64": b64,
        "KEY_ALIAS": props["KEY_ALIAS"],
        "KEY_PASSWORD": props["KEY_PASSWORD"],
        "KEYSTORE_PASSWORD": props["KEYSTORE_PASSWORD"],
    }
    for name, val in secrets.items():
        status = put_secret(name, val, key_id, pub_b64)
        print(f"{name}: HTTP {status}  (len={len(val)})")

if __name__ == "__main__":
    main()
