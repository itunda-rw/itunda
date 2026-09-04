export const theTwoLookupsThatWereSupposedToAgree = {
  slug: 'the-two-lookups-that-were-supposed-to-agree',
  title: 'The two DNS lookups that were supposed to agree with each other',
  date: '2026-09-05',
  author: 'Security Team',
  tags: ['security', 'backend', 'networking', 'kotlin'],
  excerpt:
    "Our webhook-delivery code had a genuinely careful SSRF defense — it resolved a merchant's hostname and rejected anything private, loopback, or link-local before ever sending a request. It was also completely bypassable, and the bug wasn't in the check. It was in the assumption that the code doing the checking and the code doing the connecting were looking at the same DNS answer.",
  content: `
We build webhook delivery on the assumption that a merchant's endpoint hostname is untrusted input, the same way we treat any URL a customer hands us. \`WebhookUrlPolicy\` reflects that: HTTPS-only, no credentials in the URL, no \`localhost\`, no IP literals. And at the moment we're actually about to deliver — not just when the merchant first configures the webhook — it goes further and resolves the hostname, checking every returned address against loopback, link-local, site-local, multicast, carrier-grade NAT (\`100.64.0.0/10\`), and IPv6 unique-local ranges. Reading it fresh, it's more careful than most real-world implementations we've seen. It's also not the code that decides where the request actually goes.

## Two lookups, one hostname, no reason to assume they match

\`WebhookUrlPolicy.parseForDelivery\` calls \`InetAddress.getAllByName(uri.host)\`, checks the results, and — if everything looks public — hands back the original hostname-based URI. That URI then goes to \`WebhookDeliveryService.attempt()\`, which builds a request and calls \`httpClient.send(request, ...)\` using \`java.net.http.HttpClient\`.

Here's the part that's easy to miss even reading the code carefully: \`HttpClient\` doesn't reuse the address \`parseForDelivery\` already resolved. It re-resolves the hostname itself, independently, at the moment it actually opens the socket. Two DNS queries, two points in time, and nothing tying them together except an assumption that the second one will return what the first one did.

For almost any real hostname, that assumption holds. It only breaks for a hostname whose answer is designed to change — which is exactly what an attacker controlling DNS for their own webhook domain can arrange. Return a safe public address (\`8.8.8.8\` works fine) for the validation query. Return something else — loopback, a cluster-internal service, a cloud metadata endpoint — for the connection query a few milliseconds later. The validation logic never sees the second answer. It already said yes.

This is a textbook DNS-rebinding TOCTOU (CWE-918), and the reason it's worth writing about isn't that it's exotic — it's that the vulnerable code and the correct-looking code are the *same lines*. \`WebhookUrlPolicy\`'s own checks are genuinely right. The bug is entirely in what happens after they return.

## Why the obvious fixes weren't the right fixes

The textbook mitigation for this class of bug is: resolve once, pin the address you validated, and connect to that exact IP — while still presenting the original hostname for TLS certificate validation, so you're not silently breaking the connection to a legitimate server with a legitimate certificate.

\`java.net.http.HttpClient\`'s public API doesn't give you a clean way to do that. There are two real paths, and we looked hard at both before deciding neither was right for this pass:

1. **Disable hostname verification and hand-roll certificate validation** against the original hostname via a custom \`TrustManager\`. This works, and it's also exactly the kind of code that's easy to get subtly wrong in a way that reintroduces a *different* vulnerability — weakened cert-chain validation is a worse trade than the bug we're fixing.
2. **JDK 18's \`InetAddressResolverProvider\`** (JEP 418) lets you install a custom resolver that both the validation call and the HTTP client's internal resolution would share — the clean, intended fix. Our backend targets JDK 17. Upgrading the JVM to fix one file is a real, cross-cutting decision affecting every one of our 13+ backend services, not something to fold into a security patch.

Both of those are legitimate paths *someone* should evaluate someday. Neither is a same-day fix for a live, tested, payment-adjacent delivery path.

## The fix that didn't need either

The actual constraint we needed to satisfy was narrower than "control DNS resolution everywhere": we just needed the validation and the connection to share *one* lookup, for this one delivery path. OkHttp's client already supports exactly that, via a pluggable \`Dns\` interface — and switching one file's HTTP client is a much smaller, much more auditable change than either option above.

\`\`\`kotlin
object WebhookSafeDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = try {
            InetAddress.getAllByName(hostname).toList()
        } catch (e: Exception) {
            throw UnknownHostException("Webhook hostname could not be resolved: \$hostname")
        }
        return rejectUnlessAllPublic(addresses)
    }

    internal fun rejectUnlessAllPublic(addresses: List<InetAddress>): List<InetAddress> {
        if (addresses.isEmpty() || addresses.any { !WebhookUrlPolicy.isPublicAddress(it) }) {
            throw UnknownHostException("Webhook hostname must resolve only to public addresses")
        }
        return addresses
    }
}
\`\`\`

This runs the *exact same* safety check \`WebhookUrlPolicy\` already had — we didn't invent new logic, we relocated where it's authoritative. Registering it is one line:

\`\`\`kotlin
private val httpClient: OkHttpClient = OkHttpClient.Builder()
    .dns(WebhookSafeDns)
    .connectTimeout(5, TimeUnit.SECONDS)
    .callTimeout(5, TimeUnit.SECONDS)
    .build()
\`\`\`

The TOCTOU closes because there's no longer a second, independent lookup for an attacker to race. Whatever \`WebhookSafeDns.lookup()\` returns is what OkHttp actually dials — the resolution *is* the connection's resolution, not a preview of it. And because we're only overriding which addresses get dialed, TLS hostname verification is completely untouched: OkHttp still checks the certificate against the original hostname. We didn't need to go anywhere near a custom \`TrustManager\`.

\`WebhookUrlPolicy.parseForDelivery\`'s own resolution stays in place, too — it's just no longer the security boundary. It's a fast pre-check that rejects an obviously bad hostname with a clear error message before we even build a request. \`WebhookSafeDns\` is the check that actually matters, because it's the one wired to the socket.

## Proving it, not just believing it

We didn't want to ship this on "looks right." The test that mattered wasn't "does a fully public address pass" or "does a fully private address fail" — those were already covered before this fix existed. The one that actually proves the TOCTOU is closed is a *mixed* resolution, the exact shape a rebinding attack produces:

\`\`\`kotlin
Given("a rebinding attack: the same lookup returns one public and one internal address") {
    When("OkHttp asks WebhookSafeDns to resolve it before connecting") {
        val addresses = listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("169.254.169.254"))
        Then("the whole resolution is rejected, since this is the only lookup used to connect") {
            shouldThrow<UnknownHostException> { WebhookSafeDns.rejectUnlessAllPublic(addresses) }
        }
    }
}
\`\`\`

And then — since a test that never fails proves nothing about what it's testing — we deliberately deleted the rejection check, reran the suite, and confirmed exactly this test failed and no others did. Restored the check, reran, green again. That's the difference between "we wrote a test for the bug" and "we confirmed the test actually catches the bug," and it's a five-minute step that costs nothing next to the confidence it buys.

## What this leaves open

Closing this doesn't mean webhook delivery has no remaining exposure. The backend pod this code runs in still has fully open egress at the network-policy layer — there's no Kubernetes \`NetworkPolicy\` restricting outbound traffic today, so a *different* class of SSRF (not this DNS-rebinding shape, but a more basic one) would still reach whatever's reachable on the pod's network. That's a real, separate piece of defense-in-depth, and it needs its own careful design — not least because on our current single-node setup, the database itself is reachable only via the node's own private IP, which is exactly the kind of address range a naive "block all private ranges" egress rule would also have to let through. Fixing the application-layer bug first, and being honest that the network layer isn't fixed yet, felt like the right order to do this in.
`,
};
