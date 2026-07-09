# Fact-Checked Toss to Rwanda Map

This document records the external facts used to align Itunda with Toss-style product logic for Rwanda. It should be updated whenever public Toss services, Rwanda rails, or local market data change.

## Toss Public Product Surface

Official Toss sources describe these relevant capabilities:

- Home and spending: Toss says users can connect bank accounts, cards, insurance, securities accounts, and more in one unified platform.
- Money transfer: Toss describes lifetime free transfer, fraud detection, and auto transfer scheduling after maintenance hours.
- Loan: Toss describes personalized loan recommendations and comparison across banks.
- Credit score: Toss describes free credit score checking, score improvement tips, notifications, and credit management tips.
- Securities: Toss describes stock trading with an easy interface and no separate app.
- Taxes, verification, insurance, home/vehicle, utility bills, payment, rewards, Toss Prime, and business services are part of its public product surface.
- Business with Toss includes Toss Checkout, Financial Dashboard, Toss Payments, and Toss Place POS.

Sources:

- Toss public product page: https://toss.im/en
- Toss team/product overview: https://toss.im/en/team

## Rwanda Product Facts Used

### Mobile Money

MTN Rwanda describes MoMo as a way to transfer money, make payments, and do other transactions. MTN’s public MoMo page also lists MoKash, merchant, corporate, MoMoPay, virtual card, fraud reporting, and bank/agent transaction points.

Source: https://www.mtn.co.rw/momo/

### Securities

Rwanda Stock Exchange public market data confirms locally relevant listed symbols and names including:

- `BOK`: Bank of Kigali Group PLC
- `MTNR`: MTN Rwanda PLC
- `BLR`: BRALIRWA PLC
- `IMR`: I&M Bank Rwanda PLC
- `CMR`: CIMERWA PLC
- `EQTY`: Equity Bank Group PLC

RSE also describes market segments such as the Main Market, Fixed Income Board, SME Market Segment, ETFs, REITs, daily prices, market statistics, equities, bonds, and settlement/clearing through the CSD.

Source: https://www.rse.rw/

### Public Services

IremboGov is the public-service portal used as the Rwanda equivalent for Toss-style taxes/verifications/public-service payments.

Source: https://irembo.gov.rw/

## Product Copy Rules

- Do not label rails as `Live` unless Itunda has an active provider integration.
- Use `Demo`, `Target`, or `Blocked` for prototype states.
- Use current local institution/product names where known:
  - REG, not old EWSA, for electricity.
  - WASAC for water.
  - `BOK`, `MTNR`, `BLR`, `IMR`, etc. for RSE symbols.
- Keep Toss-style concepts adapted to Rwanda:
  - Toss transfer -> phone/contact/QR/RWF transfers with MoMo and bank fallback.
  - Toss credit score -> consent-based financial passport and credit score.
  - Toss Securities -> RSE equities/bonds first, global assets only where permitted.
  - Toss Payments/Toss Place -> Itunda merchant QR, POS, settlement, and reports.
  - Toss taxes/verifications -> Irembo, RRA, National ID/KYB flows.

## Implementation Rule

If a feature is not backed by a current source or an implemented integration, show it as `Demo`, `Target`, or `Blocked` in the app. Do not present it as production-live.
