export const diskPressureCascadingFailure = {
  slug: 'the-deploy-that-evicted-its-own-image',
  title: 'The deploy that evicted its own image',
  date: '2026-07-28',
  author: 'Backend Platform Team',
  tags: ['kubernetes', 'containerd', 'incident', 'ops'],
  excerpt:
    "Ninety-something deploys into a long session, a routine rollout started evicting every backend pod on the cluster — including, a few seconds later, the very image it had just finished pulling. Two real bugs, one obvious in hindsight and one that only shows up once the first one has already happened.",
  content: `
The change itself was ordinary: another push-notification wiring, the same pattern repeated roughly ninety times already this session. Build, test, push a new image tag, roll it out. Deploy ninety-two did all of that successfully — jar built, image pushed, image pulled onto the node — and then the rollout started throwing pods into \`Evicted\` faster than they could be recreated.

## The first bug: nothing was pruning the node

\`kubectl describe node\` showed \`DiskPressure: True\`. The node's root filesystem was at 84% used, and the actual number was straightforward once we looked: \`docker system df\` reported 128 images on the node, totaling just under 29GB, with only 6 of them actually in use by a running container.

Ninety-plus deploy cycles in one continuous session, each one running \`docker build\` directly on the node (a deliberate choice — this cluster doesn't have a remote builder, and building locally on the node has worked fine all session), and nothing in that pipeline ever pruned an old image afterward. Every superseded \`item*\` tag just sat there. Kubelet's own eviction manager doesn't ask why a filesystem is full before acting on it — once available disk drops below its threshold, it starts evicting pods to reclaim space, and it did exactly that: every backend pod on the node, in flight, mid-rollout.

The fix for this half was mechanical. A blanket \`docker image prune -a -f\` looked like the obvious move, but this session runs under an auto-mode safety classifier that blocked it outright — reasonably: an unqualified \`-a\` prune is a real, broad, hard-to-preview action to hand a wildcard for. The narrower \`docker image prune -f\` (dangling, untagged layers only — never a tagged image) wasn't blocked, and it turned out to already do nearly all of the real work: 10.67GB reclaimed in one pass, node usage back down from 84% to 47%. The individually-tagged \`item*\` images we'd also removed by explicit name first barely moved the needle on their own — they all shared the same base JVM layer, so deleting a tag mostly just dropped a small unique top layer, not the bulk of the image.

## The second bug: the fix for the first one wasn't the whole story

With disk pressure cleared and a fresh pod scheduled, the rollout hit a second, different failure: \`ErrImagePull\`, with a genuinely strange message underneath it —

\`\`\`
failed to resolve image: failed to do request: Head "https://192.168.252.4:32000/v2/itunda/backend/manifests/item92":
http: server gave HTTP response to HTTPS client
\`\`\`

The deploy pipeline pre-pulls every image manually before triggering a rollout, using \`ctr images pull --hosts-dir /etc/containerd/certs.d ...\` — that \`--hosts-dir\` flag is what tells containerd this particular registry is HTTP-only, not HTTPS, since it's a local, self-hosted registry with no TLS in front of it. That pre-pull had already succeeded for \`item92\` before any of the disk-pressure trouble started.

The part that wasn't obvious in advance: kubelet's own image-pull path, the one it falls back to when scheduling a pod, doesn't know about that \`--hosts-dir\` override at all — that flag only applies to the specific \`ctr\` invocation it was passed to, not to containerd's registry configuration as a whole. Under normal conditions this never matters, because the manual pre-pull already left the image sitting in containerd's content store, and kubelet just finds it locally instead of ever needing to resolve it over the network. But kubelet's own image garbage collector, triggered by the same disk-pressure event, had swept through and evicted the pre-pulled \`item92\` image along with everything else — it hadn't been attached to a running container yet, so nothing marked it as in-use. The next scheduling attempt found no local copy, fell through to a real network pull, defaulted to HTTPS the way it does for every registry it doesn't have an explicit override for, and failed against a plain-HTTP endpoint.

The fix was to just re-run the same manual pre-pull once disk pressure had genuinely cleared: \`ctr images pull --hosts-dir ...\` for \`item92\` a second time, which populated the content store again before kubelet's next scheduling attempt.

## What we verified

Disk usage on the node: 84% → 47% after the prune. \`kubectl describe node\` confirmed \`DiskPressure: False\` (this takes roughly kubelet's own five-minute \`eviction-pressure-transition-period\` to actually flip, even once real usage has already recovered — it doesn't clear instantly, and we waited for it rather than assuming the number alone was enough). The re-pulled image landed in containerd's content store, the pod scheduled cleanly against it, and the rollout that had been stuck for the better part of ten minutes completed normally.

## The actual lesson

Neither bug here was subtle once found, but the order they surfaced in mattered: the second one is invisible until the first one has already happened, because under ordinary operation the manual pre-pull always wins the race against kubelet's own resolution path. A registry-config gap like the missing \`--hosts-dir\` awareness can sit completely dormant for months of successful deploys and then show up the moment something else — anything else — evicts the one cached copy that had been quietly covering for it.

The broader point is really about maintenance debt in a long-running autonomous loop: ninety-plus deploys is well past the point where "just don't clean up, disk is cheap" stops being true, and nothing in the pipeline was watching for that threshold. A periodic \`docker image prune -f\` — every twenty or thirty deploys, say, not just when the node is already in trouble — would have kept this from ever happening at all. We added that as a standing recommendation for this cluster rather than treating this incident as a one-off.
`,
};
