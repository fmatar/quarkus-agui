---
name: Quarkus AG-UI SRE Workbench
description: High-density Cyber-Industrial Telemetry & SRE Command Center for Tri-Protocol (AG-UI + MCP + A2A) Agent Operations
colors:
  bg-substrate: "#070a12"
  bg-panel: "rgba(13, 19, 34, 0.75)"
  bg-panel-dark: "rgba(11, 15, 25, 0.95)"
  border-subtle: "rgba(56, 189, 248, 0.12)"
  border-glow: "rgba(56, 189, 248, 0.25)"
  accent-cyan: "#38bdf8"
  accent-emerald: "#10b981"
  accent-amber: "#f59e0b"
  accent-crimson: "#ef4444"
  accent-purple: "#a855f7"
  text-primary: "#f1f5f9"
  text-secondary: "#94a3b8"
  text-muted: "#64748b"
typography:
  display:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.2
  body:
    fontFamily: "ui-sans-serif, system-ui, -apple-system, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.5
  mono:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace"
    fontSize: "0.75rem"
    fontWeight: 500
    lineHeight: 1.4
rounded:
  sm: "8px"
  md: "12px"
  lg: "16px"
  xl: "24px"
spacing:
  xs: "4px"
  sm: "8px"
  md: "16px"
  lg: "24px"
  xl: "32px"
---

# Design System: Cyber-Industrial Telemetry & SRE Command

## 1. Visual Theme & Philosophy
The Workbench rejects generic enterprise SaaS white-box templates in favor of a specialized **Cyber-Industrial SRE Command Center**. Deep obsidian glass substrates (`#070a12`, `rgba(13, 19, 34, 0.75)`), sub-pixel hairline cyan borders (`rgba(56, 189, 248, 0.12)`), and high-contrast phosphor accents deliver clear scanability under high operational density.

## 2. Color Roles & Signal Conduits
- **Substrate & Panels**: `#070a12` with subtle grid mesh backdrop; frosted glass cards with 16px backdrop blur.
- **Emerald (`#10b981`)**: Healthy microservices, running agent threads, connected SSE protocols.
- **Cyan (`#38bdf8`)**: Live AG-UI text tokens, MCP tool dispatch markers, active interactive focus.
- **Amber (`#f59e0b`)**: CPU throttling, latency alerts, and Human-in-the-Loop (HITL) authorization gates.
- **Crimson (`#ef4444`)**: Service outages, degraded pods, and stream abort triggers.
- **Purple (`#a855f7`)**: Sentinel-A2A multi-agent delegations and concentric architecture audits.

## 3. Surface Layout (3-Pane Responsive Architecture)
1. **Left Rail (`ClusterStatePanel`)**: Live cluster topology, pod replicas, CPU gauges, replica +/- controls, and 1-click A2A specialist skill delegations.
2. **Center Stage (`AgentChatContainer` + `MessageHistory` + `StreamRenderer`)**: Stream timeline displaying user instructions, OpsPilot responses, embedded MCP tool pills, A2A delegation cards, live typing tokens, and prominent HITL approval modals.
3. **Right Drawer (`ProtocolInspector`)**: SSE protocol wire inspector with filter tabs (`ALL`, `AG-UI`, `MCP`, `A2A`), formatted JSON payloads, and frame counters.

## 4. Components & Interactive Affordances
- **Action Buttons**: Sleek gradient fills with subtle micro-scale clicks (`active:scale-95`).
- **Telemetry Cards**: Frosted cards with hairline borders (`ring-1 ring-slate-800/80`) illuminating with cyan micro-glow on hover.
- **Approval Gate**: High-visibility amber-to-slate gradient card with authorization and abort buttons.
