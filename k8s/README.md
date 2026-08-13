# Running Ledger-Core on k3s (demo)

What this demonstrates: **process-level HA** — a crashed pod gets rescheduled automatically,
and a rolling deploy doesn't drop traffic. What it does **not** demonstrate: node-level HA.
All 3 replicas run on one EC2 instance, so this proves nothing about surviving a machine
failure — see [Design decisions worth noting](../README.md#design-decisions-worth-noting)
in the main README for the full framing, including what was deliberately left out and why.

---

## 1. Install k3s on a fresh EC2 instance

A `t3.medium` (2 vCPU / 4GiB) is the size these manifests are sized for. Ubuntu/Amazon
Linux, SSH'd in:

```bash
curl -sfL https://get.k3s.io | sh -
# k3s installs as a systemd service and starts immediately
sudo k3s kubectl get nodes    # confirm the single node is Ready

# Optional but convenient: use plain `kubectl` instead of `sudo k3s kubectl`
mkdir -p ~/.kube
sudo cp /etc/rancher/k3s/k3s.yaml ~/.kube/config
sudo chown $(id -u):$(id -g) ~/.kube/config
kubectl get nodes
```

## 2. Build the image and load it into k3s

No registry involved — the same `Dockerfile` this repo already builds with Render, built
locally on this node and imported straight into k3s's containerd store:

```bash
git clone <this-repo-url> && cd bank
docker build -t ledger-core:latest .
docker save ledger-core:latest | sudo k3s ctr images import -
```

(If you'd rather push to a real registry — ghcr.io, Docker Hub — that works too: push there
instead and change `image:`/`imagePullPolicy` in `k8s/deployment.yaml` accordingly. Not
needed for this demo.)

## 3. Point the config at something real

Edit `k8s/configmap.yaml`:
- `SPRING_DATASOURCE_URL` — **required**, the app won't start without a reachable Postgres.
  Point it at the same Neon instance the Render deployment uses, or at this host's own
  docker-compose Postgres via the node's private IP (`hostname -I`), not `localhost` — pods
  have their own network namespace and can't see the host's `localhost`.
- `KAFKA_BOOTSTRAP_SERVERS` / `REDIS_HOST` — optional. The app starts and serves requests
  fine without them reachable (see the main README) — leave the placeholders if you only
  care about the HA demo below, or point them at the docker-compose instances the same way.

Edit `k8s/secret.yaml` with real values — it ships with obvious placeholders, not anything
you'd actually want applied as-is.

## 4. Apply and verify

```bash
kubectl apply -f k8s/configmap.yaml -f k8s/secret.yaml -f k8s/deployment.yaml -f k8s/service.yaml

kubectl rollout status deployment/ledger-core   # waits for all 3 to become Ready
kubectl get pods -l app=ledger-core -o wide      # should show 3 Running, same NODE column (that's the point)
```

Reach it:
```bash
kubectl port-forward svc/ledger-core 8080:80
curl http://localhost:8080/api/health
```

---

## 5. Demonstrate: a killed pod gets rescheduled

```bash
kubectl get pods -l app=ledger-core                      # note one pod's name
kubectl delete pod <one-of-the-pod-names>

kubectl get pods -l app=ledger-core -w                    # watch a replacement appear within seconds
```

The Deployment's `replicas: 3` is a standing declaration, not a one-time action — the
controller notices one is missing and creates a new one immediately. This is the actual
mechanism; nothing app-specific makes it happen.

## 6. Demonstrate: a rolling deploy doesn't drop traffic

In one terminal, hit the Service in a loop (through the same port-forward from step 4):
```bash
while true; do
  curl -s -o /dev/null -w "%{http_code} %{time_total}s\n" http://localhost:8080/api/health
  sleep 0.2
done
```

In another terminal, trigger a rollout — bumping an env var is the smallest possible
change that forces one (an image tag bump works the same way):
```bash
kubectl set env deployment/ledger-core DEMO_ROLLOUT_MARKER="$(date +%s)"
kubectl rollout status deployment/ledger-core
```

Watch the first terminal while the rollout runs: every line should read `200`, with no gap.
That's `maxUnavailable: 0` in `deployment.yaml` — a replacement pod has to pass its
readinessProbe before the old one it's replacing is torn down, so the Service always has 3
working backends, even mid-rollout.

To roll out a real code change instead of just the marker: rebuild and re-import the image
with a new tag (step 2), then `kubectl set image deployment/ledger-core ledger-core=ledger-core:<new-tag>`.

---

## What was deliberately left out, and why

No Helm chart, no kustomize overlays, no HPA/autoscaling, no service mesh, no Ingress
controller, no multi-node config — a toy app demonstrating pod-level HA doesn't need any of
them, and this project prefers the smallest deployment that proves the concept over
undemonstrated complexity (the same philosophy behind [everything else documented in the
main README](../README.md#design-decisions-worth-noting)). The natural next step for
*real*, node-level HA — multi-node, e.g. EKS across availability zones — is deliberately
out of scope here; it's a genuinely different (and non-trivial) exercise from what this
demo sets out to show.
