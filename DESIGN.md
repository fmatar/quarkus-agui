---
name: Nomad Concierge & AG-UI Protocol Studio
description: Precision Travel Instrument & Editorial Concierge Desk for Tri-Protocol (AG-UI + MCP + A2A) Agent Operations
colors:
  bg-substrate: "#f6f5f2"
  bg-rail: "#efece6"
  bg-surface: "#ffffff"
  bg-subtle: "#f3f0ea"
  border-hairline: "#e4e0d8"
  border-strong: "#cfcac0"
  accent-terracotta: "#c84b31"
  accent-emerald: "#1f6e50"
  accent-indigo: "#3b4cca"
  accent-ochre: "#9a4a06"
  ink-primary: "#141413"
  ink-secondary: "#575551"
  ink-muted: "#78756f"
typography:
  display:
    fontFamily: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "1.125rem"
    fontWeight: 700
    lineHeight: 1.25
  body:
    fontFamily: "'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, sans-serif"
    fontSize: "0.875rem"
    fontWeight: 400
    lineHeight: 1.55
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

# Design System: Precision Travel Instrument & Editorial Concierge Desk (`seed:cdbde94d37fa1dbc`)

## 1. Visual Theme & Philosophy
The Nomad Concierge & AG-UI Protocol Studio replaces dark neon cyber-terminal clichés with a **Precision Travel Instrument & Editorial Concierge Desk** inspired by Dieter Rams industrial clarity and Monocle travel folios. Warm anodized alabaster (`#F6F5F2`), brushed limestone (`#EFECE6`), and crisp paper surfaces (`#FFFFFF`) frame deep obsidian typography (`#141413`), 1px `#E4E0D8` structural borders, and tabular numerals.

## 2. Color Roles & Protocol Taxonomy Signals
- **Ground & Surfaces**: Anodized Alabaster (`#F6F5F2`), Brushed Limestone Rail (`#EFECE6`), Crisp Paper Cards (`#FFFFFF`).
- **Obsidian Ink (`#141413`) & Muted Stone (`#575551`)**: High-contrast (`>10:1` primary, `>6.5:1` secondary) editorial legibility.
- **Terracotta (`#C84B31`)**: Primary interactive selection, active streaming indicators, and Human-in-the-Loop (`hitl:approval`) gates.
- **Forest Emerald (`#1F6E50`)**: Verified A2A specialist delegations (`a2a:discovery`, `a2a:task`, `a2a:artifact`) and Frankfurter FX audits.
- **Indigo (`#3B4CCA`)**: MCP capabilities (`mcp:tool`, `mcp:resource`, `mcp:prompt`, `mcp:sampling`).
- **Warm Ochre (`#9A4A06`)**: Client-side AG-UI surface rendering (`tool:ui`).

## 3. Surface Layout (3-Pane Responsive Studio)
1. **Left Rail (`ClusterStatePanel` — Trip Dossier)**: Live destination dossier (Open-Meteo weather, Frankfurter FX conversion, sanctuary budget gauge, active local soundtrack) and 1-click destination/A2A triggers.
2. **Center Stage (`AgentChatContainer` + `MessageHistory` + `AssistantTurn` + `InputBar`)**: Unified `AssistantTurn` component kit rendering `ReasoningAccordion`, `ExecutionTimeline` with `ProtocolCallBadge`, `StreamingMarkdownBody`, `MediaGallery` (image lightbox, playable 30s `.m4a`/`.mp3` iTunes preview, video reel, PDF folio), `StructuredResponseBlock` (`single_select` / `multi_select` choice cards + actions), `HitlApprovalCard`, `TurnFooter`, and `/` slash-command autocomplete.
3. **Right Drawer (`ProtocolInspector`)**: Collapsible AG-UI SSE wire inspector with protocol filters (`ALL`, `AG-UI`, `MCP`, `A2A`), `ProtocolCallBadge` tags, and expandable JSON payloads.
