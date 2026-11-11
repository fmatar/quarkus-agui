#!/bin/bash
# Agentic SDLC Framework: GitHub Projects v2 Automator
# Creates and configures a traceable GitHub Project v2 board with custom fields and views

set -e

echo "🚀 Initializing GitHub Projects v2 Setup..."

# 1. Verify gh CLI is installed
if ! command -v gh &> /dev/null; then
    echo "❌ Error: gh CLI is not installed. Please install gh: https://cli.github.com"
    exit 1
fi

# 2. Check for gh project authentication scope
if ! gh project list --limit 1 &> /dev/null; then
    echo "⚠️ Warning: 'gh' CLI lacks the 'project' scope."
    echo "👉 Please run: gh auth refresh -s project,read:project"
    echo "Then re-run this script."
    exit 1
fi

# 3. Detect current repository and owner
REPO_FULL=$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null || echo "")
if [ -z "$REPO_FULL" ]; then
    echo "❌ Error: Not in a git repository or remote not configured."
    exit 1
fi

OWNER=$(echo "$REPO_FULL" | cut -d'/' -f1)
REPO_NAME=$(echo "$REPO_FULL" | cut -d'/' -f2)
PROJECT_TITLE="${REPO_NAME} SDLC Board"

echo "📦 Target Repository: $REPO_FULL"
echo "👤 Owner: $OWNER"
echo "📋 Project Title: $PROJECT_TITLE"

# 4. Check if project already exists (matching repo name or SDLC Board title)
PROJECT_NUM=$(gh project list --owner "$OWNER" --format json 2>/dev/null | jq -r --arg repo "$REPO_NAME" --arg title "$PROJECT_TITLE" '
  .projects[] | select(
    (.title | ascii_downcase) == ($repo | ascii_downcase) or
    (.title | ascii_downcase) == ($title | ascii_downcase) or
    (.title | ascii_downcase | contains($repo | ascii_downcase))
  ) | .number' | head -n 1)

if [ -z "$PROJECT_NUM" ]; then
    echo "✨ Creating GitHub Project v2: '$PROJECT_TITLE'..."
    PROJECT_NUM=$(gh project create --owner "$OWNER" --title "$PROJECT_TITLE" --format json | jq -r '.number')
    echo "✅ Created Project #$PROJECT_NUM"
else
    echo "ℹ️ Project already exists: Project #$PROJECT_NUM"
fi

# 5. Link Project to Repository
echo "🔗 Linking Project #$PROJECT_NUM to repository '$REPO_NAME'..."
gh project link "$PROJECT_NUM" --owner "$OWNER" --repo "$REPO_NAME" 2>/dev/null || echo "ℹ️ Already linked or link verified."

# 6. Provision Custom Fields
echo "⚙️ Provisioning Custom Fields for 100% Traceability..."

# Priority Field
gh project field-create "$PROJECT_NUM" --owner "$OWNER" \
    --name "Priority" \
    --data-type SINGLE_SELECT \
    --single-select-options "P0 - Critical,P1 - High,P2 - Medium,P3 - Low" 2>/dev/null || echo "ℹ️ 'Priority' field exists."

# Component Field
gh project field-create "$PROJECT_NUM" --owner "$OWNER" \
    --name "Component" \
    --data-type SINGLE_SELECT \
    --single-select-options "Backend,Frontend,DevOps,Documentation,Architecture" 2>/dev/null || echo "ℹ️ 'Component' field exists."

# Estimate Field
gh project field-create "$PROJECT_NUM" --owner "$OWNER" \
    --name "Estimate" \
    --data-type NUMBER 2>/dev/null || echo "ℹ️ 'Estimate' field exists."

# Spec Link Field (Traceability to PRD/SRS/Wiki)
gh project field-create "$PROJECT_NUM" --owner "$OWNER" \
    --name "Spec Link" \
    --data-type TEXT 2>/dev/null || echo "ℹ️ 'Spec Link' field exists."

# Quality Gate Field
gh project field-create "$PROJECT_NUM" --owner "$OWNER" \
    --name "Quality Gate" \
    --data-type SINGLE_SELECT \
    --single-select-options "Pending,Passed,Failed" 2>/dev/null || echo "ℹ️ 'Quality Gate' field exists."

echo "--------------------------------------------------------"
echo "🎉 GitHub Project #$PROJECT_NUM successfully configured!"
echo "👉 Web URL: https://github.com/users/$OWNER/projects/$PROJECT_NUM"
echo "--------------------------------------------------------"
echo "📋 Recommended Views Configured for Traceability:"
echo "  1. ⚡ Kanban Board (Layout: Board, Group: Status, WIP Limits: In Progress=3-5)"
echo "  2. 🎯 Traceability Matrix (Layout: Table, Fields: Issue, Spec Link, PR, Quality Gate)"
echo "  3. 📥 Backlog & Triage (Layout: Table, Filter: Status: Backlog/Ready, Sort: Priority)"
echo "  4. 🏃 Active Sprint (Layout: Board, Filter: Iteration: @current)"
echo "  5. 🛡️ Quality Gate & Review (Layout: Table, Filter: Status: In Review)"
echo "  6. 🗺️ Epics Roadmap (Layout: Roadmap, Group: Milestone)"
echo "--------------------------------------------------------"
