# WulfPak — Social Network Graph Roadmap

Force-directed social graph of all contacts and their connections. New bottom-nav tab. Tap a node → PersonDetailScreen. Nodes colored by dominant relationship category. Rendered on Compose Canvas, no new dependencies.

Scoped 2026-05-02.

---

## Design decisions (locked)

| Decision | Choice |
|---|---|
| Entry point | New bottom-nav tab "Graph" (4th tab) |
| Tap behavior | Navigate to PersonDetailScreen |
| Color scheme | FAMILY = tertiary, FRIEND = primary, WORK = secondary, OTHER = outline |
| Rendering | Compose Canvas + pointerInput — no graph library dep |
| Layout algorithm | Fruchterman-Reingold spring embedder (pure Kotlin, coroutine) |

---

## Step 1 — Data layer

**Files:** `PersonRelationshipDao.kt`

- [x] Add `@Query("SELECT * FROM person_relationships") fun getAllOnce(): List<PersonRelationship>` to `PersonRelationshipDao`
  - Returns raw rows (not the bidirectional `PersonConnection` view) — avoids duplicate edges in the graph

No schema change, no migration needed.

---

## Step 2 — Graph data model + layout engine

**New files:** `core-logic/src/main/java/.../graph/GraphNode.kt`, `GraphEdge.kt`, `GraphLayoutEngine.kt`

- [ ] `GraphNode(id: UUID, name: String, category: RelCategory, closenessScore: Float?)`
- [ ] `GraphEdge(fromId: UUID, toId: UUID, category: RelCategory, closenessScore: Float)` — ViewModel resolves this as `(fromPerson.closenessScore ?: 0.3f)`
- [ ] `GraphLayoutEngine` — pure `object`, no Compose deps

  ```kotlin
  object GraphLayoutEngine {
      fun layout(nodes: List<GraphNode>, edges: List<GraphEdge>, width: Float, height: Float): Map<UUID, Offset>
  }
  ```

  Algorithm: Fruchterman-Reingold
  - Initialize positions randomly within bounds
  - Each iteration: compute repulsive forces (all pairs), attractive forces (edges only)
  - **Attractive force is weighted by `closenessScore`** — close contacts pull harder, so tight clusters form naturally. Pass `closenessScore` into `GraphEdge`; default to `0.3f` if null (unscored contact still has some pull).
  - Cool temperature each iteration (`t = t * 0.95f`)
  - Run 300 iterations
  - Clamp final positions within `[padding, width-padding] × [padding, height-padding]`
  - `Offset` from `androidx.compose.ui.geometry` — only Compose dep (acceptable in `:core-logic` since it's already a transitive dep; alternatively move to `:app` if you prefer)

  > Node mass can be uniform or weighted by `closenessScore` — start uniform, tune later.

---

## Step 3 — GraphViewModel

**New file:** `app/src/main/java/.../ui/graph/GraphViewModel.kt`

- [ ] `AndroidViewModel`, DB + `familyInferenceEngine` via `getApplication<AppApplication>()`
- [ ] Load all non-me persons + all relationship rows in `init`
- [ ] Build `List<GraphNode>` — dominant category per person = most frequent `RelCategory` across their connections; fall back to `OTHER`
- [ ] Build `List<GraphEdge>` — one edge per raw `PersonRelationship` row
- [ ] Run `GraphLayoutEngine.layout()` in `viewModelScope.launch(Dispatchers.Default)` — emits to `layoutState`
- [ ] Expose:
  - `nodes: StateFlow<List<GraphNode>>`
  - `edges: StateFlow<List<GraphEdge>>`
  - `positions: StateFlow<Map<UUID, Offset>>` — empty until layout finishes
  - `isLoading: StateFlow<Boolean>`
- [ ] Viewport state (pan offset + scale) lives in the Composable as `remember` state — not in ViewModel

---

## Step 4 — GraphScreen

**New file:** `app/src/main/java/.../ui/graph/GraphScreen.kt`

- [ ] Split into two composables so AS Preview works without a ViewModel:
  - **`GraphCanvas`** — stateless, takes `nodes`, `edges`, `positions`, `onNodeTap` as params; contains all Canvas drawing logic
  - **`GraphScreen`** — thin wrapper: collects VM state, passes it into `GraphCanvas`
- [ ] `fun GraphScreen(onNavigateToPerson: (UUID) -> Unit, viewModel: GraphViewModel = viewModel())`
- [ ] Show `CircularProgressIndicator` while `isLoading`
- [ ] `fun GraphCanvas(nodes: List<GraphNode>, edges: List<GraphEdge>, positions: Map<UUID, Offset>, onNodeTap: (UUID) -> Unit, modifier: Modifier = Modifier)`
- [ ] `Box(Modifier.fillMaxSize())` wrapping the Canvas
- [ ] `Modifier.pointerInput` — two gestures:
  - **Pan:** `detectDragGestures` → accumulate `panOffset: Offset`
  - **Pinch-to-zoom:** `detectTransformGestures` → accumulate `scale: Float` (clamp `0.3f..3f`)
- [ ] Draw order inside Canvas (apply `translate(panOffset)` + `scale(scale)` via `withTransform`):
  1. Edges — `drawLine(color = outline, strokeWidth = 1.5f.dp)` between position pairs
  2. Node circles — `drawCircle(color = categoryColor, radius = nodeRadius)`
  3. Name labels — `drawContext.canvas.nativeCanvas.drawText(name, x, y, paint)` (clipped to avoid overlap at low zoom)
- [ ] Tap detection: `detectTapGestures` → find nearest node within `nodeRadius * 2` of tap point (in canvas coords after un-transforming), call `onNodeTap(node.id)`
- [ ] Node radius: `8.dp` base; scale up slightly by `(closenessScore ?: 0.3f)` so closer contacts appear larger
- [ ] Category colors resolve from `MaterialTheme.colorScheme` — pass them into the Canvas draw block as `val`s before entering Canvas scope (can't call `@Composable` inside Canvas)
- [ ] Add `@Preview` at the bottom of `GraphScreen.kt`:
  ```kotlin
  @Preview(showBackground = true, widthDp = 380, heightDp = 700)
  @Composable
  fun GraphCanvasPreview() {
      val ids = List(8) { UUID.randomUUID() }
      val nodes = listOf(
          GraphNode(ids[0], "Alice",   RelCategory.FRIEND, 0.9f),
          GraphNode(ids[1], "Bob",     RelCategory.FRIEND, 0.7f),
          GraphNode(ids[2], "Carol",   RelCategory.FRIEND, 0.5f),
          GraphNode(ids[3], "Dave",    RelCategory.WORK,   0.4f),
          GraphNode(ids[4], "Eve",     RelCategory.WORK,   0.6f),
          GraphNode(ids[5], "Frank",   RelCategory.FAMILY, 0.95f),
          GraphNode(ids[6], "Grace",   RelCategory.FAMILY, 0.8f),
          GraphNode(ids[7], "Heather", RelCategory.OTHER,  0.2f),
      )
      val edges = listOf(
          GraphEdge(ids[0], ids[1], RelCategory.FRIEND, 0.8f),
          GraphEdge(ids[0], ids[2], RelCategory.FRIEND, 0.5f),
          GraphEdge(ids[1], ids[2], RelCategory.FRIEND, 0.6f),
          GraphEdge(ids[3], ids[4], RelCategory.WORK,   0.4f),
          GraphEdge(ids[5], ids[6], RelCategory.FAMILY, 0.9f),
          GraphEdge(ids[0], ids[7], RelCategory.OTHER,  0.2f),
      )
      // Static positions for preview — skip layout engine
      val positions = mapOf(
          ids[0] to Offset(190f, 200f), ids[1] to Offset(120f, 300f),
          ids[2] to Offset(260f, 320f), ids[3] to Offset(80f,  500f),
          ids[4] to Offset(160f, 520f), ids[5] to Offset(310f, 180f),
          ids[6] to Offset(330f, 280f), ids[7] to Offset(230f, 460f),
      )
      WulfPakTheme {
          GraphCanvas(nodes = nodes, edges = edges, positions = positions, onNodeTap = {})
      }
  }
  ```

---

## Step 5 — Navigation

**File:** `app/src/main/java/.../navigation/AppNavigation.kt`

- [ ] Add `const val GRAPH = "graph"` to `Routes` object
- [ ] Add `import androidx.compose.material.icons.filled.Hub`
- [ ] Add `TopLevelDest(Routes.GRAPH, Icons.Default.Hub, "Graph")` to `TOP_LEVEL_DESTS`
- [ ] Add composable destination:
  ```kotlin
  composable(Routes.GRAPH) {
      GraphScreen(onNavigateToPerson = { id -> navController.navigate(Routes.personDetail(id)) })
  }
  ```
- [ ] Add `import com.github.maskedkunisquat.wulfpak.ui.graph.GraphScreen`

---

## Compile checkpoints

```bash
./gradlew :app:compileDebugKotlin   # after each step
./gradlew assembleDebug && ./gradlew installDebug   # final
```

---

## Manual test checklist

- [ ] Bottom nav shows "Graph" (Hub icon) as 4th tab
- [ ] Loading spinner shown while layout computes
- [ ] All contacts appear as nodes; no "me" contact rendered
- [ ] Edges connect persons with at least one relationship
- [ ] Node colors match category (spot-check 3 contacts)
- [ ] Pan and pinch-to-zoom both work smoothly
- [ ] Tapping a node navigates to the correct PersonDetailScreen
- [ ] Tapping empty space does nothing
- [ ] Contacts with no connections appear as isolated nodes (not missing)
- [ ] Graph re-renders correctly after adding a new connection

---

## Deferred

- **Edge labels** — show relationship label on hover/long-press (cluttered at scale)
- **Filter by category** — toggle chips to show/hide Friend / Work / Family edges
- **Inferred family edges** — optionally render dashed edges from `FamilyInferenceEngine`
- **Node search** — highlight a specific contact's node by name
