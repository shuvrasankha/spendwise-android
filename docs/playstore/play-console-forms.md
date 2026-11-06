# Play Console answers for SpendWise 1.8.1

Answers for each Play Console form, taken from what the app does in 1.8.1 (code 20) and from
`docs/privacy-policy.html`. Copy them in as written. Where a choice is a judgement call, the line says so.

- Package: `com.spendwise.money` (`com.spendwise.app` is taken on Play; the GitHub build keeps it)
- Privacy policy: https://shuvrasankha.github.io/spendwise-android/privacy-policy.html
- Account deletion: https://shuvrasankha.github.io/spendwise-android/delete-account.html
- Contact email: contact.spend.wise.app@gmail.com

---

## Fix before uploading: the install permission

The manifest asks for `REQUEST_INSTALL_PACKAGES` so the GitHub build can install its own updates.
Play allows that permission only for apps whose core purpose is installing other apps, and its
Device and Network Abuse policy forbids a Play app updating itself outside Play. The updater is off
when Play installed the app, but the permission and the download code are still in the bundle, so
review is likely to reject it.

Fix: a `play` build without the permission or the GitHub updater, and a `github` build with them.
Until then, don't upload.

---

## App content › Privacy policy

URL: https://shuvrasankha.github.io/spendwise-android/privacy-policy.html

The page is published from `main`'s `docs/` folder. Merge this release first, so the live page has
the 6 November 2026 text.

## App content › App access

**All or some functionality is restricted.** Everything is behind sign-in.

Give reviewers a working email and password account with some sample data in it (expenses, income,
a budget, a goal, a holding, a debt). Instructions to paste:

> Sign in with the email and password below. The account has sample data. AI features use
> SpendWise AI by default; no API key is needed.

Don't give your own account. Create one for review.

## App content › Ads

**No, my app does not contain ads.**

## App content › Content rating (IARC questionnaire)

- Category: **All other app types** (a finance utility).
- Violence, sexuality, language, controlled substances, gambling, crude humour: **No** to all.
- **Do users interact with each other or exchange content?** **Yes**, judgement call: a debt can be
  shared with another user by email, and it then shows in their account. There is no chat, no
  public content, and no contact with strangers.
- Shares the user's location: **No**.
- Digital purchases: **No**.
- Unrestricted internet access (a web browser): **No**.

Expected rating: Everyone / PEGI 3, possibly marked "Users Interact".

## App content › Target audience and content

- Age groups: **18 and over** only.
- Could the app appeal to children: **No**.

The privacy policy says SpendWise isn't for children under 13; 18+ fits a finance app and avoids the
Families policy.

## App content › News app / Government app / Health app

**No** to each.

## App content › Financial features

Pick the personal finance or budgeting option: SpendWise records expenses, income, budgets, goals
and investments that the user types in.

It does **not**: lend money, offer credit or loans, hold or move money, link bank accounts, trade
or broker investments, offer crypto, or process payments. If the form asks about any of these,
answer **No**.

## App content › Data safety

### Overview questions

- Does your app collect or share any of the required user data types? **Yes**
- Is all of the user data collected by your app encrypted in transit? **Yes**
- Do you provide a way for users to request that their data is deleted? **Yes**: in the app
  (Settings › Delete account) and at the deletion URL above.

### Data types

"Collected" means it leaves the device. Data sent to Firebase, Cloudflare and the AI service for
the app's own use counts as processing by service providers, not sharing, so **Shared: No**
throughout. With "My own API key", AI requests go to the provider the user picked, at the user's
request. Play's rules don't count user-initiated transfers like that as sharing either.

| Data type | Collected | Shared | Ephemeral | Required? | Purposes |
|---|---|---|---|---|---|
| Personal info › Name | Yes | No | No | Optional (Google sign-in; names of people on debts) | App functionality, Account management |
| Personal info › Email address | Yes | No | No | Required (the account) | App functionality, Account management |
| Personal info › User IDs | Yes | No | No | Required (Firebase account ID) | App functionality, Account management |
| Financial info › Purchase history | Yes | No | No | Required (the expenses entered) | App functionality |
| Financial info › Other financial info | Yes | No | No | Required (income, debts, budgets, goals, investments; summaries sent to the AI) | App functionality |
| Photos and videos › Photos | Yes | No | **Yes** (read by the AI, then deleted from the phone) | Optional (receipt scan, bill photo in chat) | App functionality |
| Files and docs | Yes | No | **Yes** (CSV rows read by the AI) | Optional (CSV import) | App functionality |
| Contacts | Yes | No | No | Optional (one contact picked for a debt: name and email) | App functionality |
| App activity › Other user-generated content | Yes | No | No | Optional (notes, tags, goal names, AI questions, voice text, the "Plans this month" note) | App functionality |
| App info and performance › Crash logs | Yes | No | No | Required (release builds) | Analytics |
| App info and performance › Diagnostics | Yes | No | No | Required (Performance Monitoring, release builds) | Analytics |
| Device or other IDs | Yes | No | No | Optional (notification token, if reminders or alerts are on) | App functionality |

### Not collected

Location, audio (speech is turned into text on the phone; only the text is sent), messages,
calendar, health, web browsing, installed apps, payment card or bank details, advertising ID.

The public holiday calendar download sends no user data. It names only the country.

## Store listing

- App name: **SpendWise**
- Category: **Finance**
- Tags: Budget, Expense tracker, Personal finance

**Short description** (80 characters max; this one is 77):

> Track spending, income and debts, set budgets and goals, and ask AI about it.

**Full description** (4000 characters max):

> SpendWise is a simple, private way to see where your money goes.
>
> Add an expense in seconds: type the amount, pick a category, done. Or say it out loud, or scan a
> receipt. SpendWise keeps your expenses, income, debts, budgets, savings goals and investments in
> one place, and syncs them with the SpendWise web app.
>
> SEE YOUR MONTH AT A GLANCE
> • What you've spent, against your budget or your income
> • A "Today" mark that shows whether you're spending faster than the month is passing
> • Bills and EMIs due soon, and a spending calendar
>
> BUDGETS AND GOALS
> • A monthly budget with optional limits by category
> • "Suggest from my income": a budget built from your last three months, this month's festivals,
>   how the same month went last year, your goals, and any plans you add
> • Savings goals with the monthly amount you need to reach them
>
> DEBTS AND SPLITS
> • Track what you owe and what you're owed, with part payments
> • Split an expense with friends; share a debt with another SpendWise user
>
> INVESTMENTS
> • Holdings, SIPs, what you've put in and what it's worth now
>
> ASK ABOUT YOUR MONEY
> • "How much did I spend on food last month?" is worked out on your phone, exactly
> • Ask for advice and the AI coach answers from your own figures
> • Use SpendWise AI, or your own API key from NVIDIA, OpenAI, Google Gemini, Anthropic or Groq
>
> MORE
> • History with search, filters and CSV export; CSV import
> • Daily reminder, budget alerts, home-screen widget
> • App lock, light and dark themes
> • Works offline; syncs when you're back online
>
> PRIVATE BY DESIGN
> No ads. Your data isn't sold. The AI gets summary figures and your question, not your entry
> descriptions or notes. You can delete your account and all its data from the app at any time.

**Graphics needed:**
- App icon, 512×512: `docs/playstore/icon-512.png` (done)
- Feature graphic, 1024×500: `docs/playstore/feature-graphic.jpg` (done; source `docs/brand/spendwise-feature-graphic.svg`)
- Phone screenshots, at least 2: still needed. The ones in `docs/playstore/raw/` show the Home
  screen from before 1.8.1, so take new ones.

## Release › Release notes for 1.8.1 (500 characters max)

> • AI budget suggestions now allow for festivals, last year's same month, your goals and your plans
> • The AI chat can see your investments
> • Clearer category names, with a hint for each
> • Quick income sources: Salary, Freelance, Interest and more
> • A "Today" mark on the spending bar
> • Repeat, tags and split always visible when adding an expense
> • Set button to switch AI provider
