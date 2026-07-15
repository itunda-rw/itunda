# Kafka Active-Active

This directory separates two things that are easy to confuse:

- the open-source baseline that is safe to run today: dedicated MirrorMaker 2 workers
- the Toss-style end state for same-topic active-active: a custom mirroring contract with loop
  suppression and explicit offset-sync logic

`mirrormaker2.yaml` is the repo-native OSS baseline. It runs a dedicated MM2 deployment and uses
topic-prefix replication, which is the part stock Kafka supports well.

The baseline is now opinionated in two ways that matter for the laptop rehearsal:

- it pins explicit `topics.exclude` / `groups.exclude` safety filters instead of relying on
  upstream defaults for MM2 internal topics and Connect groups
- it lowers `refresh.topics.interval.seconds` / `refresh.groups.interval.seconds` so dedicated
  drill topics and consumer groups show up on a human time scale during local rehearsals
- it adds explicit `ephemeral-storage` requests and limits so the worker is less likely to get
  evicted silently on the low-disk Multipass node

It does **not** use HTTP readiness/liveness probes. In this repo's live `connect-mirror-maker.sh`
path on `apache/kafka:4.2.0`, the worker does real mirroring work but does not expose a probeable
HTTP listener soon enough to use as a trustworthy Kubernetes health gate.

`toss-style-mirror-contract.yaml` defines the stricter same-topic contract the platform should
eventually implement with a custom Kafka Connect connector or equivalent service.
