# VoltFlow frontend redesign architecture

This document records the frontend audit and the target component structure
before implementation. The redesign keeps the current Spring Boot API contract
and the 1–2 second polling cadence intact.

## Audit summary

- The supported frontend is React 18, Vite, TypeScript, Recharts, and
  `lucide-react`.
- Authentication uses real email-and-password registration and login through
  `POST /auth/register` and `POST /auth/login`. Both return a JWT stored under
  the existing `voltflow_jwt_token` key. Passwords are validated by the backend
  against BCrypt hashes and social authentication is not exposed.
- The intended dashboard architecture already has focused API normalization,
  an abortable polling hook, reusable cards, an accessible dialog, historical
  charts, registration, toast feedback, and safe user-facing errors.
- The current merge result bypasses those components with a monolithic
  prototype. It also introduces static/fallback telemetry, inaccessible custom
  overlays, duplicate registration UI, and TypeScript build failures.
- `usePollingResource` already prevents overlapping requests, aborts on
  unmount, schedules the next poll only after completion, constrains polling to
  1–2 seconds, and preserves data references when responses are structurally
  unchanged.
- Home detail data polls independently while history, events, and
  recommendations load only when the selected home changes or the user retries.
- Recharts animations are disabled for live data, which avoids resetting
  animations every polling cycle.

## Target route/view model

- `/` — public VoltFlow landing experience.
- `/login` — email-and-password sign-in.
- `/register` — email, password, and password-confirmation account creation.
- `/dashboard` — authenticated dashboard; unauthenticated visitors are sent to
  the login experience.
- The existing nginx SPA fallback remains unchanged.

The view router is intentionally small and dependency-free because the project
currently has no routing package. Browser history and `popstate` provide direct
links and back/forward navigation without changing backend paths.

## Component layers

```text
App
├── LandingPage
├── LoginPage
│   ├── CharacterGroup
│   └── ApplianceCharacter[]
└── Dashboard
    ├── Header
    ├── OverviewStats
    ├── HomeCard[]
    │   └── ApplianceCharacter[]
    ├── RegistrationModal
    └── HomeDetailModal
        ├── DetailTabs
        ├── ApplianceCharacterGrid
        ├── ApplianceTelemetryPanel
        ├── EnergyCharts
        ├── RecommendationPanel
        └── EventTimeline
```

Shared layers:

- `characters/config.ts` — central appliance geometry, palette, and
  accessible naming configuration.
- `characters/ApplianceCharacter.tsx` — memoized character renderer controlled
  by type, expression, activity, anomaly, selection, gaze, and size props.
- `characters/CharacterGroup.tsx` — coordinated groups with throttled pointer
  gaze and form reaction states.
- `presentation/appliancePresentation.ts` — maps normalized API data to visual
  state, status copy, warning explanation, and suggested action. Character
  components never consume raw backend shapes.
- `components/*` — reusable cards, dialogs, feedback, charts, and domain
  presentation.
- `styles/` — tokens, primitives, character geometry, page layouts, dialogs,
  responsive rules, and reduced-motion behavior, composed by `styles.css`. No
  runtime Tailwind dependency is required.

## State and performance boundaries

- Authentication state remains at `App`.
- Rapid home-list telemetry remains inside the dashboard polling resource.
- Live selected-home telemetry remains inside `HomeDetailModal`; list polling
  pauses while a detail or registration dialog is active to avoid duplicate
  requests.
- History, events, and recommendations remain separate from rapid telemetry.
- Modal, tab, selected-appliance, filter, and auth interaction state stay local
  to their owning components.
- Character gaze and expression state is isolated from telemetry state.
- Memoized home cards and characters avoid redrawing unchanged structures.
- Historical chart transforms are memoized and historical chart panels are
  isolated from live appliance chart updates.
- Pointer tracking is bounded and scheduled at most once per animation frame.

## Compatibility constraints

- Keep home and telemetry API URLs, payload fields, JWT storage, timeout behavior,
  pagination safeguards, abort semantics, and safe error mapping.
- Do not add Google sign-in, OAuth, a temporary login, or mock telemetry.
- Home registration continues to expand appliance quantities into the current
  flat request payload.
- Delete actions remain connected to the existing home and appliance DELETE
  endpoints.
- All precise Watt, kWh, cost, limit, breach, health, tariff, budget, and
  timestamp values remain visible alongside character visuals.
