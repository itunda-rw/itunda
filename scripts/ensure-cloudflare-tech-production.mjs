const token = process.env.CLOUDFLARE_API_TOKEN;
const accountId = process.env.CLOUDFLARE_ACCOUNT_ID;

if (!process.env.CI || !token || !accountId) {
  process.exit(0);
}

const response = await fetch(
  `https://api.cloudflare.com/client/v4/accounts/${accountId}/pages/projects/itunda-tech`,
  {
    method: "PATCH",
    headers: {
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify({ production_branch: "main" }),
  },
);

if (!response.ok) {
  throw new Error(`Cloudflare project update failed: HTTP ${response.status}`);
}

const result = await response.json();
if (!result.success) {
  throw new Error(`Cloudflare project update failed: ${JSON.stringify(result.errors ?? [])}`);
}

console.log("Cloudflare itunda-tech production branch set to main.");
