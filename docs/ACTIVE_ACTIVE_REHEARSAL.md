# Active-Active Rehearsal (Multipass, 2026-07-13)

> **What this is and isn't.** A local, two-VM rehearsal of the active-active failover *shape*
> Toss actually runs in production (see `docs/TOSS_ARCHITECTURE_FACTS.md` §1, §2, §7) — not a
> copy of Toss's real infrastructure. Toss's real setup is Cluster API + CAPO managing an
> actual OpenStack control plane across physical Pods, plus real MirrorMaker2 across real
> datacenters. Two 4GB Multipass VMs on one laptop cannot reproduce that, and don't try to
> here. What this rehearsal *can* prove, and did: does itunda's own failover logic (replica
> promotion, data-loss checking) actually work when tested against something real, not just
> assumed from reading the code. Same "verify before claiming" discipline as the rest of this
> repo's docs.

## Setup

Two Multipass Ubuntu 22.04 VMs, `itunda-dc-a` (192.168.252.2) and `itunda-dc-b`
(192.168.252.3), 2 CPU / 4GB RAM / 10GB disk each (started at 2GB RAM, resized after hitting
real memory pressure — see "What went wrong" below). Each runs Docker with:

- `mysql:8.0.39` — matching the real version pinned in `infra/docker-compose.yml`
- `apache/kafka:3.7.0` — official Apache Kafka image, KRaft mode (no Zookeeper), one broker
  per VM, single independent cluster each

## What's real and verified here

**MySQL active-active (replica-promotion pattern) — fully proven, live.**

1. GTID-based async replication wired dc-a (primary) → dc-b (replica): `CHANGE REPLICATION
   SOURCE TO ... SOURCE_AUTO_POSITION=1`.
2. Verified live: a write on dc-a's `itunda.active_active_check` table appeared on dc-b within
   2 seconds.
3. Verified replication survives a real restart: both VMs were stopped and resized (2G→4G RAM),
   and dc-b's replica thread reconnected and caught up (`Seconds_Behind_Source: 0`) without any
   manual intervention.
4. **Full failover drill, not just steady-state replication**: wrote a row to dc-a, stopped
   dc-a's MySQL container entirely (simulating a real outage), confirmed dc-b had both prior
   rows with zero data loss, ran `STOP REPLICA; RESET REPLICA ALL;` to promote dc-b, then wrote
   a new row directly to dc-b and confirmed it landed. dc-b now behaves as a real standalone
   primary post-promotion.

This is the honest shape of what "active-active" can mean for a ledger system without naive
multi-master (which risks write conflicts corrupting double-entry integrity) — active-active
at the read/serving layer, single-writer-with-fast-promotion at the data layer. Matches how
real financial systems, including Toss's own documented pattern, actually do this.

5. **Two-way topology restored after the drill, not left broken.** Brought dc-a's stopped
   MySQL container back up and pointed it at dc-b (now the primary) via `CHANGE REPLICATION
   SOURCE TO ... SOURCE_AUTO_POSITION=1` — no re-clone needed. GTID auto-position correctly
   resolved the set difference even though dc-a still had its own pre-outage transaction
   history locally; it just needed the one new transaction dc-b generated under its own
   server UUID after promotion. Verified by checking dc-a's actual data, not just replica
   status: dc-a picked up the dropped test table, all 5 real Flyway migrations, and every row
   of real application data written during the live backend test below (see "Real backend,
   booted against this rehearsal's infrastructure") — including a user whose `kycVerified`
   flag was flipped mid-test. Confirms the failover pattern is reusable, not a one-shot demo.

## Real backend, booted against this rehearsal's infrastructure

Not just a MySQL/Kafka toy — the actual Spring Boot backend (`services/backend`) was booted
against dc-b (post-promotion primary) with a real Redis added alongside it on the same VM, to
live-verify the new `identity`/compliance module (see `docs/API_SPECIFICATION.md`'s Identity
and Compliance sections, and `docs/TOSS_PARITY_MATRIX.md`'s Compliance row for the full
account). Flyway applied all 5 real migrations cleanly against the promoted node; a real user
was registered, submitted KYC, got reviewed by a promoted admin, and had `kycVerified` flip to
`true` — all against this rehearsal's own infrastructure, not a throwaway local container.

## What's real but not fully proven — Kafka

Both Kafka clusters are real, independently running KRaft brokers, matching the "two fully
separate clusters, not a stretched one" half of Toss Securities' documented pattern
(`docs/TOSS_ARCHITECTURE_FACTS.md` §2). What did **not** get verified:

- **Real MirrorMaker2 (`connect-mirror-maker.sh`) never got its embedded Connect REST server
  to bind**, across several real, diagnosed attempts:
  - First failure: both flows' embedded herders defaulted to the same REST listener
    (`http://:8083`) in one JVM — confirmed via log grep showing the identical listener
    config twice. A per-flow `listeners` override (`dc-a->dc-b.listeners = ...`) was tried and
    did not take effect (logs still showed 8083 for both after the change).
  - Second attempt: split into two separate processes/containers, one per flow, using
    `connect-mirror-maker.sh mm2.properties --clusters <X>`. Note `--clusters` filters by each
    flow's *target* cluster, not its source — counterintuitive, cost real debugging time to
    work out from log inspection alone.
  - Even fully isolated (one herder per process, own container, own network namespace,
    hostname resolution confirmed working, JVM alive with a normal thread count), the REST
    server still never logged a successful bind and never accepted connections on 8083.
    Root cause not found — could be an Alpine/JVM-in-nested-VM interaction specific to this
    environment, not chased further given time already spent.
- A fallback plain `kafka-console-consumer.sh | kafka-console-producer.sh` relay was tried as
  a lighter substitute. It also did not produce a clean, verified round-trip in the time spent
  on it — console-producer's default fire-and-forget behavior made it unclear whether messages
  were actually landing or silently dropped during topic auto-creation races.

**Bottom line: Kafka cross-cluster mirroring is a real, undemonstrated gap in this rehearsal**,
not a proven capability. Don't cite this rehearsal as evidence Kafka mirroring works for
itunda — only that two independent brokers can be stood up and reached from either VM.

## How this maps to Toss's real pattern

See `docs/TOSS_ARCHITECTURE_FACTS.md` §1 (Toss Bank: active-active across two DCs, both
serving live traffic), §2 (Toss Securities: two independent Kafka clusters, bidirectional
mirroring, Split DNS producers, GSLB active-standby consumers, custom offset-sync tool), and
§7 (Toss Payments: OpenStack + Cluster API, "OKS"). This rehearsal only touches the smallest,
cheapest slice of that — MySQL failover logic — and explicitly does not attempt Cluster API,
OpenStack, or a real multi-broker Kafka cluster with proper replication-factor guarantees
(both brokers here are `replication.factor=1`, meaning no in-cluster redundancy either,
separate from the cross-cluster mirroring gap above).

## Cleanup

The two VMs are still running as of this writeup (`itunda-dc-a`, `itunda-dc-b`, 4GB RAM each —
8GB total host memory committed). Tear down with:

```bash
multipass delete itunda-dc-a itunda-dc-b
multipass purge
```

## Honest next steps, if this rehearsal continues

1. Root-cause the MM2 REST server bind issue, or replace the tool (e.g., try Kafka's older
   `kafka-mirror-maker.sh` MM1, or a minimal custom Connect worker config without the
   `connect-mirror-maker.sh` driver's flow-splitting behavior).
2. If Kafka mirroring gets proven, add the loop-prevention verification Toss's real design
   relies on (bidirectional mirroring with `IdentityReplicationPolicy` requires MM2's internal
   cycle detection to actually work — untested here since mirroring itself never ran).
3. Multi-broker replication factor ≥2 per cluster, to rehearse in-cluster redundancy separate
   from cross-cluster DR.
