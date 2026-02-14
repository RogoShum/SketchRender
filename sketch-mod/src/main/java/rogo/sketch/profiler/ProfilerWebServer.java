package rogo.sketch.profiler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import rogo.sketch.SketchRender;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class ProfilerWebServer {
    private static HttpServer server;
    private static final int PORT = 25586;

    public static void start() {
        if (server != null)
            return;
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext("/", new StaticHandler());
            server.createContext("/api/data", new DataHandler());
            server.setExecutor(null);
            server.start();
            SketchRender.LOGGER.info("Profiler Server started on port " + PORT);
        } catch (IOException e) {
            SketchRender.LOGGER.error("Failed to start Profiler Server", e);
        }
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
            SketchRender.LOGGER.info("Profiler Server stopped");
        }
    }

    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = getHtmlContent();
            t.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    static class DataHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String json = Profiler.get().getLastSessionJson();
            byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
            t.getResponseHeaders().set("Content-Type", "application/json");
            t.sendResponseHeaders(200, bytes.length);
            OutputStream os = t.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    private static String getHtmlContent() {
        return """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                        <title>Profiler</title>
                        <link rel="preconnect" href="https://fonts.googleapis.com">
                        <link href="https://fonts.googleapis.com/css2?family=JetBrains+Mono:wght@400;500;700&family=Inter:wght@400;600;700&display=swap" rel="stylesheet">
                        <style>
                            :root {
                                --font-ui: 'Inter', system-ui, sans-serif;
                                --font-mono: 'JetBrains Mono', monospace;
                                --ease: cubic-bezier(0.25, 1, 0.5, 1);
                                --anim-dur: 0.3s;
                            }

                            /* === THEMES === */
                            [data-theme="day"] {
                                --bg: #f8f9fa; --card: #ffffff; --text: #212529; --sub: #6c757d;
                                --border: #dee2e6; --accent: #0d6efd; --highlight: rgba(13, 110, 253, 0.1);
                                --radius: 6px; --row-radius: 4px;
                                --shadow: 0 2px 8px rgba(0,0,0,0.05);
                                --deco: '☀️'; --sel-mask: rgba(13, 110, 253, 0.1); --sel-line: #0d6efd;
                                --anim-dur: 0.25s;
                            }
                            [data-theme="night"] {
                                --bg: #0d1117; --card: #161b22; --text: #e6edf3; --sub: #8b949e;
                                --border: #30363d; --accent: #58a6ff; --highlight: rgba(88, 166, 255, 0.15);
                                --radius: 6px; --row-radius: 4px;
                                --shadow: 0 8px 24px rgba(0,0,0,0.4);
                                --deco: '🌙'; --sel-mask: rgba(88, 166, 255, 0.15); --sel-line: #58a6ff;
                                --anim-dur: 0.25s;
                            }
                            [data-theme="jelly"] {
                                --bg: #f0f9ff; --card: rgba(255, 255, 255, 0.65); --text: #0369a1; --sub: #0ea5e9;
                                --border: #e0f2fe; --accent: #0ea5e9; --highlight: rgba(14, 165, 233, 0.15);
                                --radius: 24px; --row-radius: 12px;
                                --shadow: 8px 8px 20px rgba(14, 165, 233, 0.15), inset 1px 1px 2px #fff;
                                --deco: '🍬'; --sel-mask: rgba(14, 165, 233, 0.15); --sel-line: #0ea5e9;
                                --ease: cubic-bezier(0.68, -0.6, 0.32, 1.6);
                                --anim-dur: 0.5s;
                            }
                            [data-theme="jelly"] .card {
                                backdrop-filter: blur(16px); border: 4px solid #fff;
                                box-shadow: 0 10px 25px -5px rgba(14, 165, 233, 0.25), 0 4px 10px -4px rgba(14, 165, 233, 0.1);
                            }
                            [data-theme="jelly"] button, [data-theme="jelly"] select {
                                border-radius: 50px; background: linear-gradient(135deg, #fff, #e0f2fe);
                                border: 2px solid #fff; box-shadow: 0 2px 5px rgba(14, 165, 233, 0.1);
                            }
                            [data-theme="fresh"] {
                                --bg: #fff7ed; --card: #fff; --text: #431407; --sub: #9a3412;
                                --border: #431407; --accent: #f97316; --highlight: #ffedd5;
                                --radius: 0px; --row-radius: 0px;
                                --shadow: 4px 4px 0 #431407;
                                --deco: '🍊'; --sel-mask: rgba(249, 115, 22, 0.2); --sel-line: #431407;
                                --anim-dur: 0.15s;
                            }
                            [data-theme="fresh"] button { box-shadow: 3px 3px 0 var(--border); transition: none; }
                            [data-theme="fresh"] button:active { transform: translate(3px,3px); box-shadow: none; }
                            [data-theme="fresh"] button.active { background: var(--accent); color: #fff; box-shadow: 2px 2px 0 var(--border); }
                            [data-theme="neon"] {
                                --bg: #020202; --card: #0a050a; --text: #fae8ff; --sub: #d946ef;
                                --border: #4a044e; --accent: #d946ef; --highlight: rgba(217, 70, 239, 0.2);
                                --radius: 2px; --row-radius: 2px;
                                --shadow: 0 0 15px rgba(217, 70, 239, 0.15);
                                --deco: '🔮'; --sel-mask: rgba(217, 70, 239, 0.15); --sel-line: #d946ef;
                                --anim-dur: 0.2s;
                            }
                            [data-theme="neon"] .card { border: 1px solid #333; }
                            [data-theme="neon"] button { text-transform: uppercase; font-size: 10px; letter-spacing: 1px; border-color: var(--accent); color: var(--accent); background: #000; }
                            [data-theme="neon"] button:hover { background: var(--accent); color: #000; box-shadow: 0 0 15px var(--accent); }
                            [data-theme="neon"] button.active { background: var(--accent); color: #000; box-shadow: 0 0 20px var(--accent); }

                            /* === LAYOUT === */
                            body { margin: 0; padding: 20px; background: var(--bg); color: var(--text); font-family: var(--font-ui); transition: background 0.3s; overflow-y: scroll; min-height: 100vh; display: flex; flex-direction: column; }
                            .container { max-width: 1920px; margin: 0 auto; width: 100%; display: flex; flex-direction: column; gap: 20px; flex: 1; }

                            header { flex: 0 0 auto; display: flex; flex-wrap: wrap; gap: 15px; align-items: center; justify-content: space-between; }
                            h1 { margin: 0; display: flex; align-items: center; gap: 10px; font-size: 1.4rem; font-weight: 800; letter-spacing: -0.5px; }
                            h1::after { content: var(--deco); animation: bounce 2s infinite; font-size: 1.2rem; }
                            @keyframes bounce { 0%,100%{transform:translateY(0)} 50%{transform:translateY(-5px)} }

                            .controls-row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
                            .btn-group { display: flex; gap: 0; }

                            button, select {
                                height: 30px; padding: 0 12px; font-family: inherit; font-size: 12px; font-weight: 600;
                                background: var(--card); color: var(--text); border: 1px solid var(--border);
                                border-radius: var(--radius); cursor: pointer; outline: none;
                                transition: all 0.2s var(--ease); display: flex; align-items: center; justify-content: center;
                            }
                            button:hover, select:hover { filter: brightness(0.97); z-index: 1; }
                            button.active { background: var(--accent); color: #fff; border-color: var(--accent); z-index: 2; }

                            .btn-l { border-radius: var(--radius) 0 0 var(--radius) !important; border-right-width: 0; }
                            .btn-m { border-radius: 0 !important; border-right-width: 0; }
                            .btn-r { border-radius: 0 var(--radius) var(--radius) 0 !important; }

                            .dashboard { display: grid; grid-template-columns: 1fr 1.4fr; gap: 20px; align-items: start; }
                            .col-left { display: flex; flex-direction: column; gap: 20px; position: sticky; top: 20px; height: fit-content; }

                            .card { background: var(--card); border-radius: var(--radius); box-shadow: var(--shadow); padding: 15px; display: flex; flex-direction: column; border: 1px solid var(--border); transition: transform 0.3s var(--ease); }
                            .card-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; border-bottom: 2px solid rgba(0,0,0,0.03); padding-bottom: 8px; }
                            .card-title { font-weight: 700; font-size: 13px; color: var(--sub); text-transform: uppercase; letter-spacing: 0.5px; }

                            .vis-container { width: 100%; position: relative; background: rgba(0,0,0,0.015); border-radius: 4px; overflow: hidden; }
                            .timeline-wrap { height: 220px; cursor: crosshair; }

                            .pie-card-body { width: 100%; display: flex; justify-content: center; align-items: center; transition: height var(--anim-dur) var(--ease); overflow: hidden; }
                            .pie-wrap { width: 100%; aspect-ratio: 1 / 1; transition: all var(--anim-dur) var(--ease); opacity: 1; display: flex; align-items: center; justify-content: center; }
                            .pie-wrap.collapsed { height: 0; opacity: 0; margin: 0; aspect-ratio: auto; }
                            canvas { display: block; width: 100%; height: 100%; }

                            .col-right { display: flex; flex-direction: column; min-width: 0; }
                            .table-card { flex: 1; display: flex; flex-direction: column; padding: 0; overflow: visible; height: auto; transition: min-height 0.2s; }

                            .table-header-box {
                                padding: 15px; background: var(--bg); border-bottom: 2px solid rgba(0,0,0,0.03);
                                display: flex; justify-content: space-between; align-items: center;
                                position: sticky; top: 0; z-index: 20; border-radius: var(--radius) var(--radius) 0 0;
                            }
                            .table-card .table-header-box { margin: 0; background: var(--card); border-radius: var(--radius) var(--radius) 0 0; }

                            .table-container { flex: 1; overflow: visible; user-select: none; }

                            table { width: 100%; border-collapse: separate; border-spacing: 0; font-size: 12px; table-layout: fixed; }
                            th { text-align: left; padding: 10px 12px; color: var(--sub); background: var(--card); border-bottom: 1px solid var(--border); cursor: pointer; user-select: none; }
                            th:hover { color: var(--accent); background: var(--highlight); }
                            td { border-bottom: 1px solid var(--border); padding: 0; vertical-align: middle; }

                            .row-inner {
                                display: flex; align-items: center; padding: 6px 12px; height: 34px;
                                cursor: pointer;
                            }

                            /* Selection Styles */
                            tr:hover td { background: rgba(0,0,0,0.03); }
                            tr.selected td { background: var(--highlight); border-top: 1px solid var(--sel-line); border-bottom: 1px solid var(--sel-line); }

                            tr.selected td:first-child {
                                border-left: 1px solid var(--sel-line);
                                border-top-left-radius: var(--row-radius);
                                border-bottom-left-radius: var(--row-radius);
                            }
                            tr.selected td:last-child {
                                border-right: 1px solid var(--sel-line);
                                border-top-right-radius: var(--row-radius);
                                border-bottom-right-radius: var(--row-radius);
                            }

                            tr.dimmed td { opacity: 0.3; filter: grayscale(1); transition: opacity 0.2s; }
                            tr.dimmed:hover td { opacity: 0.7; }

                            .toggle {
                                width: 16px; height: 16px; display: inline-flex; align-items: center; justify-content: center;
                                margin-right: 6px; color: var(--sub); border-radius: 3px; font-size: 9px; cursor: pointer;
                                transition: transform 0.2s var(--ease), background 0.2s;
                            }
                            .toggle:hover { background: var(--highlight); color: var(--accent); }
                            .toggle.closed { transform: rotate(-90deg); }

                            .metric-cell { font-family: var(--font-mono); text-align: right; padding-right: 12px; color: var(--text); overflow: hidden; white-space: nowrap; text-overflow: ellipsis; }

                            .pct-track {
                                width: 90%; height: 16px; margin-left: auto; margin-right: 12px;
                                position: relative; display: flex; align-items: center;
                                border: 1px solid var(--border);
                                border-radius: 4px; background: rgba(0,0,0,0.02); overflow: hidden;
                            }
                            .pct-bar { height: 100%; border-radius: 2px; transition: width 0.3s; position: absolute; left: 0; top: 0; }
                            .pct-text { position: absolute; right: 4px; font-size: 10px; font-family: var(--font-mono); color: var(--text); z-index: 2; font-weight: 600; text-shadow: 0 0 5px var(--bg); mix-blend-mode: hard-light; }
                            [data-theme="day"] .pct-text { mix-blend-mode: multiply; }

                            #tooltip {
                                position: fixed; background: rgba(10, 10, 10, 0.95); color: #fff;
                                padding: 8px 12px; border-radius: 6px; font-size: 12px;
                                pointer-events: none; z-index: 9999; display: none;
                                box-shadow: 0 8px 20px rgba(0,0,0,0.4); border: 1px solid rgba(255,255,255,0.15); backdrop-filter: blur(8px);
                            }
                            .tt-head { color: var(--accent); font-weight: 700; margin-bottom: 4px; border-bottom: 1px solid rgba(255,255,255,0.2); padding-bottom: 4px; }
                            .tt-row { display: flex; justify-content: space-between; gap: 15px; font-family: var(--font-mono); margin-top: 2px; opacity: 0.9; }

                            @media (max-width: 900px) {
                                .dashboard { grid-template-columns: 1fr; }
                                .col-left { position: static; }
                                .pie-wrap { max-width: 400px; margin: 0 auto; }
                            }
                        </style>
                    </head>
                    <body>
                        <div id="tooltip"></div>
                        <div class="container">
                            <header>
                                <h1>Profiler</h1>
                                <div class="controls-row">
                                    <span id="loading-txt" style="font-size:11px; color:var(--sub); font-family:var(--font-mono)"></span>
                                    <select id="theme-sel" onchange="App.setTheme(this.value)">
                                        <option value="day">☀️ Day</option>
                                        <option value="night">🌙 Night</option>
                                        <option value="jelly">🍬 Jelly</option>
                                        <option value="fresh">🍊 Fresh</option>
                                        <option value="neon">🔮 Neon</option>
                                    </select>
                                    <div class="btn-group">
                                        <button onclick="App.setUnit('ms')" id="btn-ms" class="btn-l active">ms</button>
                                        <button onclick="App.setUnit('us')" id="btn-us" class="btn-m">μs</button>
                                        <button onclick="App.setUnit('ns')" id="btn-ns" class="btn-r">ns</button>
                                    </div>
                                    <button onclick="App.resetView()">Reset</button>
                                </div>
                            </header>
                            <div class="dashboard">
                                <div class="col-left">
                                    <div class="card">
                                        <div class="card-header">
                                            <span class="card-title">Timeline</span>
                                            <span id="tl-info" style="font-family:var(--font-mono); font-weight:400; opacity:0.8; font-size:11px;"></span>
                                        </div>
                                        <div class="vis-container timeline-wrap"><canvas id="c-timeline"></canvas></div>
                                    </div>
                                    <div class="card" id="card-dist">
                                        <div class="card-header">
                                            <span class="card-title">Distribution</span>
                                            <button style="height:20px; font-size:10px; padding:0 8px;" onclick="App.togglePie()">Toggle</button>
                                        </div>
                                        <div class="pie-card-body">
                                            <div class="vis-container pie-wrap" id="pie-box"><canvas id="c-pie"></canvas></div>
                                        </div>
                                    </div>
                                </div>
                                <div class="col-right" id="col-metrics">
                                    <div class="card table-card" id="table-card">
                                        <div class="table-header-box">
                                            <span class="card-title">Metrics</span>
                                            <div class="btn-group">
                                                <button id="chk-avg" class="btn-l active" onclick="App.toggleMetric('avg')">Avg</button>
                                                <button id="chk-min" class="btn-m" onclick="App.toggleMetric('min')">Min</button>
                                                <button id="chk-max" class="btn-r" onclick="App.toggleMetric('max')">Max</button>
                                            </div>
                                        </div>
                                        <div class="table-container">
                                            <table id="tree-table"><thead id="table-head"></thead><tbody id="table-body"></tbody></table>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>
                        <script>
                            const App = {
                                rawData: null, globalStats: new Map(), rootNode: null, flatNodes: [],
                                unit: localStorage.getItem('sketch_unit') || 'ms',
                                activeMetrics: new Set(['avg']),
                                sort: { col: 'avg', dir: -1 },
                                theme: localStorage.getItem('sketch_theme') || 'day',
                                dpr: window.devicePixelRatio || 1,
                                viewStart: 0, viewEnd: 0, totalDur: 0, maxDepth: 1,
                                selection: { active: false, start: 0, end: 0 },
                                hoverNode: null,
                                selectedPaths: new Set(),
                                collapsed: new Set(), colors: {},
                                animState: new Map(), animLoopReq: null,

                                init: async function() {
                                    try {
                                        const res = await fetch('/api/data');
                                        const json = await res.json();
                                        if(!json.cycles || !json.cycles.length) throw new Error("No Data");
                                        App.rawData = json;
                                        // CHANGED: Use backend stats if available
                                        if(json.stats) {
                                            App.globalStats.clear();
                                            for(let k in json.stats) App.globalStats.set(k, json.stats[k]);
                                        } else {
                                            App.calcGlobalStats(json.cycles);
                                        }
                                        App.buildTree(json.cycles.find(c => c.length > 0) || []);

                                        App.resetView();
                                        App.setupCanvas();

                                        document.getElementById('theme-sel').value = App.theme;
                                        App.setTheme(App.theme);
                                        App.setUnit(App.unit);

                                        App.setupTableInteractions();
                                        document.getElementById('loading-txt').textContent = `${json.cycles.length} Frames`;
                                    } catch(e) { console.error(e); }
                                },

                                calcGlobalStats: function(cycles) {
                                    App.globalStats.clear();
                                    cycles.forEach(events => {
                                        if(!events.length) return;
                                        const stack = []; const t0 = events[0].ts;
                                        events.forEach(e => {
                                            const t = e.ts - t0;
                                            if(e.t === 'START') {
                                                const p = stack.length ? stack[stack.length-1].path : "Root";
                                                const path = (stack.length === 0 && e.n === 'root') ? "Root" : (p + "/" + e.n);
                                                stack.push({path, start:t});
                                            } else if(e.t === 'LINEAR') {
                                                if(stack.length) {
                                                    const p = stack[stack.length-1];
                                                    if(p.lastL) App.recStat(p.lastL.path, t - p.lastL.start);
                                                    p.lastL = { path: p.path+"/"+e.n, start:t };
                                                }
                                            } else if(e.t === 'END') {
                                                if(!stack.length) return;
                                                const n = stack.pop();
                                                App.recStat(n.path, t - n.start);
                                                if(n.lastL) App.recStat(n.lastL.path, t - n.lastL.start);
                                            }
                                        });
                                    });
                                    for(let s of App.globalStats.values()) s.avg = s.total / s.count;
                                },

                                calcSelectionStatsRevised: function() {
                                    const map = new Map();
                                    const tS = App.selection.active ? App.selection.start : App.viewStart;
                                    const tE = App.selection.active ? App.selection.end : App.viewEnd;
                                    const hasSel = App.selectedPaths.size > 0;

                                    App.flatNodes.forEach(n => {
                                        if(n.name === 'Root') return;
                                        if(hasSel && !App.selectedPaths.has(n.path)) return;

                                        const s = Math.max(tS, n.start);
                                        const e = Math.min(tE, n.end);
                                        const intersectDur = Math.max(0, e - s);

                                        if(intersectDur > 0) {
                                            if(!map.has(n.path)) map.set(n.path, {
                                                min:intersectDur, max:intersectDur, total:intersectDur, count:1
                                            });
                                            else {
                                                const r = map.get(n.path);
                                                r.min = Math.min(r.min, intersectDur);
                                                r.max = Math.max(r.max, intersectDur);
                                                r.total += intersectDur;
                                                r.count++;
                                            }
                                        }
                                    });
                                    for(let v of map.values()) {
                                        v.avg = v.total / v.count;
                                    }
                                    return map;
                                },

                                recStat: function(path, dur) {
                                    let s = App.globalStats.get(path);
                                    if(!s) App.globalStats.set(path, { min:dur, max:dur, total:dur, count:1, avg:0 });
                                    else { s.min = Math.min(s.min, dur); s.max = Math.max(s.max, dur); s.total += dur; s.count++; }
                                },
                                buildTree: function(events) {
                                    const t0 = events[0].ts;
                                    const root = { name: 'Root', path: 'Root', start: 0, end: 0, depth: 0, children: [] };
                                    const stack = [root]; const flat = [root]; let maxD = 0;
                                    events.forEach(e => {
                                        const t = e.ts - t0;
                                        const p = stack[stack.length-1];
                                        if(e.t === 'START') {
                                            if(e.n === 'root') return;
                                            const path = p.path + "/" + e.n;
                                            const n = { name:e.n, path, start:t, end:-1, depth:p.depth+1, children:[], parent:p };
                                            p.children.push(n); stack.push(n); flat.push(n); maxD = Math.max(maxD, n.depth);
                                            App.animState.set(n.path, 1.0);
                                        } else if(e.t === 'LINEAR') {
                                            const last = p.children[p.children.length-1];
                                            if(last && last.type==='L' && last.end===-1) last.end=t;
                                            const path = p.path + "/" + e.n;
                                            const n = { name:e.n, path, start:t, end:-1, depth:p.depth+1, children:[], parent:p, type:'L' };
                                            p.children.push(n); flat.push(n);
                                            App.animState.set(n.path, 1.0);
                                        } else if(e.t === 'END') {
                                            if(stack.length>1) { const n = stack.pop(); n.end=t; n.children.forEach(c => { if(c.end===-1) c.end=t; }); } else stack[0].end = t;
                                        }
                                    });
                                    const finalT = events[events.length-1].ts - t0;
                                    flat.forEach(n => { if(n.end === -1) n.end = finalT; });
                                    App.rootNode = root; App.flatNodes = flat; App.totalDur = Math.max(1, root.end); App.maxDepth = Math.max(1, maxD);
                                },

                                setTheme: function(t) {
                                    App.theme = t; localStorage.setItem('sketch_theme', t);
                                    document.documentElement.setAttribute('data-theme', t);
                                    App.colors = {}; App.draw(); App.renderTable();
                                },
                                setUnit: function(u) {
                                    App.unit = u; localStorage.setItem('sketch_unit', u);
                                    ['ms','us','ns'].forEach(x => document.getElementById('btn-'+x).classList.toggle('active', x===u));
                                    App.draw(); App.renderTable();
                                },
                                toggleMetric: function(m) {
                                    if(App.activeMetrics.has(m)) App.activeMetrics.delete(m); else App.activeMetrics.add(m);
                                    ['avg','min','max'].forEach(x => document.getElementById('chk-'+x).classList.toggle('active', App.activeMetrics.has(x)));
                                    App.renderTable();
                                },
                                doSort: function(col) {
                                    if(App.sort.col === col) App.sort.dir *= -1;
                                    else { App.sort.col = col; App.sort.dir = -1; }
                                    App.renderTable();
                                },
                                togglePie: function() {
                                    document.getElementById('pie-box').classList.toggle('collapsed');
                                    setTimeout(App.draw, 100);
                                },
                                resetView: function() {
                                    App.viewStart = 0; App.viewEnd = App.totalDur;
                                    App.selection.active = false;
                                    App.selectedPaths.clear();
                                    App.draw(); App.renderTable();
                                },

                                toggleNode: function(path) {
                                    if(App.collapsed.has(path)) App.collapsed.delete(path); else App.collapsed.add(path);
                                    App.renderTable();
                                },

                                // CHANGED: Simple Toggle (Add/Remove) logic for Multi-select
                                toggleSelectPath: function(path) {
                                    if(App.selectedPaths.has(path)) App.selectedPaths.delete(path);
                                    else App.selectedPaths.add(path);
                                    App.draw(); App.renderTable();
                                },

                                clearSelection: function() {
                                    App.selectedPaths.clear();
                                    App.draw(); App.renderTable();
                                },

                                // CHANGED: Recursive visibility check for proper nesting
                                isNodeVisible: function(n) {
                                    let p = n.parent;
                                    while(p) {
                                        if(App.collapsed.has(p.path)) return false;
                                        p = p.parent;
                                    }
                                    return true;
                                },

                                getMousePos: function(canvas, evt) {
                                    const rect = canvas.getBoundingClientRect();
                                    return {
                                        x: (evt.clientX - rect.left) * (canvas.width / rect.width),
                                        y: (evt.clientY - rect.top) * (canvas.height / rect.height)
                                    };
                                },

                                draw: function() {
                                    App.drawTimeline();
                                    App.drawPie();
                                    const s = App.selection.active ? App.selection.start : App.viewStart;
                                    const e = App.selection.active ? App.selection.end : App.viewEnd;
                                    document.getElementById('tl-info').textContent = App.selection.active ? `Selected: ${App.fmt(e-s)}` : `View: ${App.fmt(e-s)}`;
                                },

                                drawTimeline: function() {
                                    const cvs = document.getElementById('c-timeline');
                                    const ctx = cvs.getContext('2d');
                                    const w = cvs.width; const h = cvs.height;
                                    const range = App.viewEnd - App.viewStart;

                                    if (range <= 0 || w <= 0 || h <= 0) return;

                                    ctx.clearRect(0,0,w,h);

                                    const ROW_H = 22; const GAP = 6; const TOP = 16;
                                    const hasSel = App.selectedPaths.size > 0;

                                    App.flatNodes.forEach(n => {
                                        if(n.name==='Root') return;
                                        const isSelected = App.selectedPaths.has(n.path);
                                        if(n.end < App.viewStart || n.start > App.viewEnd) return;
                                        const x = ((n.start - App.viewStart)/range) * w;
                                        const bw = Math.max(1, ((n.end - n.start)/range) * w);
                                        const y = TOP + (n.depth-1)*(ROW_H+GAP);

                                        const dimmed = hasSel && !isSelected;
                                        if (dimmed) {
                                            App.drawBlock(ctx, x, y, bw, ROW_H, n.name, false, false, true);
                                        } else {
                                            App.drawBlock(ctx, x, y, bw, ROW_H, n.name, isSelected, App.hoverNode===n, false);
                                        }
                                    });

                                    if(App.selection.active) {
                                        const cs = getComputedStyle(document.body);
                                        const sx = ((App.selection.start - App.viewStart)/range)*w;
                                        const ex = ((App.selection.end - App.viewStart)/range)*w;
                                        ctx.fillStyle = cs.getPropertyValue('--sel-mask').trim();
                                        ctx.fillRect(0,0, sx, h); ctx.fillRect(ex,0, w-ex, h);
                                        ctx.strokeStyle = cs.getPropertyValue('--sel-line').trim();
                                        ctx.lineWidth = 2;
                                        ctx.strokeRect(sx, 0, ex-sx, h);
                                    }
                                },

                                drawPie: function() {
                                    const cvs = document.getElementById('c-pie');
                                    const ctx = cvs.getContext('2d');
                                    const w = cvs.width; const h = cvs.height;
                                    if(w <= 0 || h <= 0) return;
                                    const cx = w/2; const cy = h/2;
                                    ctx.clearRect(0,0,w,h);

                                    ctx.save();
                                    ctx.translate(w, 0);
                                    ctx.scale(-1, 1);

                                    const tS = App.selection.active ? App.selection.start : App.viewStart;
                                    const tE = App.selection.active ? App.selection.end : App.viewEnd;
                                    const dur = tE - tS;

                                    let visibleMaxDepth = 0;
                                    App.flatNodes.forEach(n => {
                                        if(n.name==='Root') return;
                                        const s = Math.max(tS, n.start);
                                        const e = Math.min(tE, n.end);
                                        if (e > s) visibleMaxDepth = Math.max(visibleMaxDepth, n.depth);
                                    });
                                    visibleMaxDepth = Math.max(1, visibleMaxDepth);

                                    const size = Math.min(w, h);
                                    const maxR = size / 2 - 20;
                                    const innerR = 30;
                                    const depthDiv = Math.max(1, visibleMaxDepth + (visibleMaxDepth > 1 ? 1 : 0));
                                    const ringW = (maxR - innerR) / depthDiv;

                                    App.pieGeom = { cx, cy, innerR, ringW, tS, tE, dur, flipped: true, w };
                                    const hasSel = App.selectedPaths.size > 0;

                                    App.flatNodes.forEach(n => {
                                        if(n.name==='Root') return;
                                        const animVal = App.animState.get(n.path) ?? 1.0;
                                        if(animVal < 0.05) return;
                                        if(n.end <= tS || n.start >= tE) return;
                                        const s = Math.max(tS, n.start);
                                        const e = Math.min(tE, n.end);
                                        if(e <= s) return;

                                        const isSelected = App.selectedPaths.has(n.path);
                                        const dimmed = hasSel && !isSelected;

                                        const aStart = -Math.PI/2 + ((s - tS)/dur) * (Math.PI*2);
                                        const aEnd = -Math.PI/2 + ((e - tS)/dur) * (Math.PI*2);

                                        ctx.globalAlpha = dimmed ? 0.1 : animVal;

                                        const rIn = innerR + (n.depth-1) * ringW;
                                        const rOut = rIn + ringW - 1;
                                        const isHov = (App.hoverNode === n);

                                        const baseCol = App.getColor(n.name);
                                        ctx.fillStyle = isHov ? App.adjustAlpha(baseCol, 0.9) : baseCol;

                                        ctx.save();
                                        if(App.theme === 'neon') {
                                                ctx.shadowBlur = (isSelected||isHov) ? 15 : 0; ctx.shadowColor = baseCol;
                                                ctx.strokeStyle = '#fff'; ctx.lineWidth = (isSelected||isHov) ? 2 : 1;
                                        } else if(App.theme === 'jelly') {
                                            const grad = ctx.createRadialGradient(cx, cy, rIn, cx, cy, rOut);
                                            grad.addColorStop(0, App.adjustLight(baseCol, 20)); grad.addColorStop(1, baseCol);
                                            ctx.fillStyle = grad;
                                            if(isSelected||isHov) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; }
                                            else ctx.strokeStyle = 'transparent';
                                        } else if(App.theme === 'fresh') {
                                            ctx.fillStyle = baseCol;
                                            ctx.strokeStyle = '#000'; ctx.lineWidth = 1;
                                        } else {
                                                if(isSelected||isHov) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5; }
                                                else ctx.strokeStyle = getComputedStyle(document.body).getPropertyValue('--bg');
                                        }

                                        ctx.beginPath();
                                        ctx.arc(cx, cy, rOut, aStart, aEnd, false);
                                        ctx.arc(cx, cy, rIn, aEnd, aStart, true);
                                        ctx.closePath();
                                        ctx.fill();
                                        ctx.stroke();
                                        ctx.restore();
                                    });

                                    ctx.globalAlpha = 1.0;
                                    ctx.restore();
                                },

                                renderTable: function() {
                                    const thead = document.getElementById('table-head');
                                    const tbody = document.getElementById('table-body');
                                    const isSel = App.selection.active;
                                    const dynStats = isSel ? App.calcSelectionStatsRevised() : App.globalStats;
                                    const mCols = Array.from(App.activeMetrics);

                                    if(!App.sort.col || (App.sort.col!=='avg' && App.sort.col!=='min' && App.sort.col!=='max')) {
                                        App.sort.col = 'avg'; App.sort.dir = -1;
                                    } else if (!mCols.includes(App.sort.col) && mCols.length > 0) {
                                        App.sort.col = 'avg';
                                    }
                                    if(mCols.length === 0) App.sort.col = 'avg';

                                    let ths = `<th style="width:35%">Stage</th>`;
                                    mCols.forEach(c => {
                                        const arrow = (App.sort.col === c) ? (App.sort.dir===-1?'↓':'↑') : '';
                                        ths += `<th class="metric-cell ${(App.sort.col===c?'sorted':'')}" onclick="App.doSort('${c}')" style="width:15%">${c.toUpperCase()} ${arrow}</th>`;
                                    });
                                    ths += `<th style="width:25%">Ratio</th>`;
                                    thead.innerHTML = `<tr>${ths}</tr>`;

                                    let html = '';
                                    function build(nodes) {
                                        const sorted = [...nodes].sort((a,b) => {
                                            const statA = dynStats.get(a.path)||{};
                                            const statB = dynStats.get(b.path)||{};
                                            const va = statA[App.sort.col] || 0;
                                            const vb = statB[App.sort.col] || 0;
                                            return (va - vb) * App.sort.dir;
                                        });

                                        sorted.forEach(node => {
                                            // CHANGED: Recursive folded check
                                            if(!App.isNodeVisible(node)) return;

                                            const stats = dynStats.get(node.path) || {min:0,max:0,avg:0,total:0};

                                            const primaryVal = stats[App.sort.col] || stats.avg || 0;
                                            const isDimmed = isSel && (primaryVal === 0);

                                            // CHANGED: Fix ratio denominator
                                            let maxRef;
                                            if (isSel) {
                                                maxRef = App.selection.end - App.selection.start;
                                            } else {
                                                // FIX: Use Root node total time for global view ratio
                                                maxRef = App.rootNode.end - App.rootNode.start;
                                            }

                                            const pct = maxRef > 0 ? Math.min(100, (stats.total / maxRef) * 100) : 0;
                                            const indent = (node.depth-1)*18;
                                            const col = App.getColor(node.name);
                                            const hasKids = node.children.length > 0;
                                            const isCol = App.collapsed.has(node.path);
                                            // CHANGED: Added onmousedown="event.stopPropagation()"
                                            const tog = hasKids ? `<div class="toggle ${isCol?'closed':''}" onmousedown="event.stopPropagation()" onclick="event.stopPropagation(); App.toggleNode('${node.path}')">▼</div>` : `<div class="toggle"></div>`;

                                            const isSelected = App.selectedPaths.has(node.path);
                                            const selClass = isSelected ? 'selected' : '';
                                            const dimClass = isDimmed ? 'dimmed' : '';

                                            html += `<tr class="${selClass} ${dimClass}" data-path="${node.path}">
                                                <td><div class="row-inner"><div style="padding-left:${indent}px; display:flex; height:100%">${Array(node.depth-1).fill('<div class="tree-spacer"><div class="tree-line"></div></div>').join('')}</div>${tog}<span style="color:${col}; margin-right:6px">●</span><b>${node.name}</b></div></td>
                                                ${mCols.map(k => `<td class="metric-cell">${App.fmt(stats[k])}</td>`).join('')}
                                                <td><div class="pct-track"><div class="pct-bar" style="width:${pct}%; background:${col}"></div><span class="pct-text">${pct.toFixed(1)}%</span></div></td>
                                            </tr>`;

                                            if(hasKids) build(node.children); // Traverse children, visibility handled by check at top
                                        });
                                    }
                                    if(App.rootNode) build(App.rootNode.children);
                                    tbody.innerHTML = html;
                                },

                                drawBlock: function(ctx, x, y, w, h, name, sel, hov, dim) {
                                    const baseCol = App.getColor(name);
                                    ctx.fillStyle = dim ? App.adjustAlpha(baseCol, 0.1) : baseCol;
                                    if(App.theme === 'neon') {
                                        ctx.shadowBlur = (sel||hov) ? 15 : 0; ctx.shadowColor = baseCol;
                                        ctx.strokeStyle = dim ? '#333' : '#fff'; ctx.lineWidth = (sel||hov) ? 2 : 1;
                                        ctx.fillRect(x, y, w, h); ctx.strokeRect(x, y, w, h); ctx.shadowBlur = 0;
                                    } else if(App.theme === 'jelly') {
                                        if(!dim) {
                                            const grad = ctx.createLinearGradient(x, y, x, y+h);
                                            grad.addColorStop(0, App.adjustLight(baseCol, 20)); grad.addColorStop(1, baseCol);
                                            ctx.fillStyle = grad;
                                        }
                                        ctx.beginPath(); ctx.roundRect(x, y, w, h, 6); ctx.fill();
                                        if(sel||hov) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 2; ctx.stroke(); }
                                    } else if(App.theme === 'fresh') {
                                        ctx.fillStyle = dim ? '#eee' : baseCol; ctx.fillRect(x, y, w, h);
                                        ctx.strokeStyle = '#000'; ctx.lineWidth = 2; ctx.strokeRect(x, y, w, h);
                                    } else {
                                        ctx.beginPath(); ctx.roundRect(x, y, w, h, 3); ctx.fill();
                                        if(sel||hov) { ctx.strokeStyle = '#fff'; ctx.lineWidth = 1.5; ctx.stroke(); }
                                    }
                                    if(w > 30) {
                                        ctx.fillStyle = (dim || App.theme==='neon') ? 'rgba(255,255,255,0.7)' : '#fff';
                                        if((App.theme==='fresh'||App.theme==='day') && !dim) ctx.fillStyle = '#000';
                                        ctx.save(); ctx.beginPath(); ctx.rect(x,y,w,h); ctx.clip();
                                        ctx.font = "600 11px Inter"; ctx.fillText(name, x+5, y+15); ctx.restore();
                                    }
                                },

                                setupCanvas: function() {
                                    const tl = document.getElementById('c-timeline');
                                    const pie = document.getElementById('c-pie');
                                    function resize() {
                                        App.dpr = window.devicePixelRatio || 1;
                                        [tl, pie].forEach(c => {
                                            const r = c.parentElement.getBoundingClientRect();
                                            c.width = r.width * App.dpr; c.height = r.height * App.dpr;
                                        });
                                        App.draw();
                                    }
                                    window.addEventListener('resize', resize);
                                    resize();

                                    let drag=false, ds=0, isTimeDrag=false;

                                    tl.addEventListener('mousedown', e => {
                                        const pos = App.getMousePos(tl, e);
                                        ds = pos.x;
                                        isTimeDrag = true;
                                        drag = true;
                                    });

                                    window.addEventListener('mouseup', (e) => {
                                        if (drag && isTimeDrag) {
                                            // If drag distance was very small, treat as click
                                            const pos = App.getMousePos(tl, e);
                                            if (Math.abs(pos.x - ds) < 5) {
                                                if(App.hoverNode) {
                                                    // Clicked a Node: Toggle Selection (Multi-select)
                                                    App.toggleSelectPath(App.hoverNode.path);
                                                } else {
                                                    // CHANGED: Click Empty Space -> Reset View (Requirement 4)
                                                    App.resetView();
                                                }
                                            }
                                        }
                                        drag = false; isTimeDrag = false;
                                    });

                                    tl.addEventListener('mousemove', e => {
                                        const pos = App.getMousePos(tl, e);
                                        const x = pos.x; const w = tl.width;
                                        const range = App.viewEnd - App.viewStart;
                                        const t = App.viewStart + (x/w)*range;

                                        if(drag && isTimeDrag) {
                                            // Time Range Drag
                                            if(Math.abs(x - ds) > 5) {
                                                const tS = App.viewStart + (ds/w)*range;
                                                App.selection.active=true; App.selection.start=Math.min(tS,t); App.selection.end=Math.max(tS,t);
                                                App.draw(); App.renderTable();
                                            }
                                        } else {
                                            const hasSel = App.selectedPaths.size > 0;
                                            let hit = null; const top=16, rowH=28;
                                            for(let i=App.flatNodes.length-1; i>=0; i--) {
                                                const n = App.flatNodes[i];
                                                // REMOVED FILTER to allow Multi-select in Timeline hover
                                                // if (hasSel && !App.selectedPaths.has(n.path)) continue;

                                                if(n.name!=='Root' && t >= n.start && t <= n.end) {
                                                    const ny = top + (n.depth-1)*rowH;
                                                    if(pos.y >= ny && pos.y <= ny+22) { hit = n; break; }
                                                }
                                            }
                                            if(App.hoverNode!==hit) { App.hoverNode=hit; tl.style.cursor=hit?'pointer':'crosshair'; App.draw(); App.showTooltip(e,hit); }
                                            else if(hit) App.showTooltip(e,hit);
                                        }
                                    });

                                    // Zoom
                                    tl.addEventListener('wheel', e => {
                                        e.preventDefault();
                                        const pos = App.getMousePos(tl, e);
                                        const w = tl.width;
                                        const range = App.viewEnd - App.viewStart;
                                        const t = App.viewStart + (pos.x/w) * range;
                                        const zoom = e.deltaY > 0 ? 1.1 : 0.9;
                                        const newRange = range * zoom;
                                        App.viewStart = t - (t - App.viewStart) * zoom;
                                        App.viewEnd = t + (App.viewEnd - t) * zoom;
                                        App.draw(); App.renderTable();
                                    }, {passive: false});

                                    tl.addEventListener('mouseleave', () => { App.hoverNode=null; document.getElementById('tooltip').style.display='none'; App.draw(); });

                                    pie.addEventListener('mousemove', e => {
                                        if(!App.pieGeom) return;
                                        const pos = App.getMousePos(pie, e);
                                        const { cx, cy, innerR, ringW, tS, tE, dur, flipped, w } = App.pieGeom;

                                        let mx = pos.x;
                                        if(flipped) mx = w - mx;
                                        mx = mx - cx;
                                        const my = pos.y - cy;

                                        const dist = Math.sqrt(mx*mx + my*my);
                                        let hit = null;
                                        if(dist > innerR) {
                                            const depth = Math.floor((dist - innerR) / ringW) + 1;
                                            let ang = Math.atan2(my, mx);
                                            let normAng = ang + Math.PI/2;
                                            if(normAng < 0) normAng += Math.PI*2;
                                            const tHit = tS + (normAng / (Math.PI*2)) * dur;
                                            hit = App.flatNodes.find(n => n.depth === depth && tHit >= n.start && tHit <= n.end && App.isNodeVisible(n));
                                            // REMOVED FILTER to allow Multi-select in Pie hover
                                            // if(App.selectedPaths.size > 0 && hit && !App.selectedPaths.has(hit.path)) hit = null;
                                        }
                                        if(App.hoverNode!==hit) { App.hoverNode=hit; pie.style.cursor=hit?'pointer':'default'; App.draw(); App.showTooltip(e,hit); }
                                        else if(hit) App.showTooltip(e,hit);
                                    });

                                    pie.addEventListener('click', (e) => {
                                        if(App.hoverNode) {
                                            App.toggleSelectPath(App.hoverNode.path);
                                        } else {
                                            // CHANGED: Click Empty in Pie -> Clear Selection Only (Requirement 2)
                                            App.clearSelection();
                                        }
                                    });
                                    pie.addEventListener('mouseleave', () => { App.hoverNode=null; document.getElementById('tooltip').style.display='none'; App.draw(); });
                                },

                                setupTableInteractions: function() {
                                    const body = document.getElementById('table-body');
                                    let drag = false;

                                    body.addEventListener('mousedown', e => {
                                        const tr = e.target.closest('tr');
                                        if(!tr) return;

                                        drag = true;
                                        const path = tr.dataset.path;
                                        // CHANGED: Table selection also follows Toggle logic
                                        App.toggleSelectPath(path);
                                    });

                                    body.addEventListener('mousemove', e => {
                                        if(!drag) return;
                                        const tr = e.target.closest('tr');
                                        if(tr && tr.dataset.path) {
                                            const node = App.flatNodes.find(n => n.path === tr.dataset.path);
                                            if(node && !App.selectedPaths.has(node.path)) {
                                                App.selectedPaths.add(node.path);
                                                App.draw(); App.renderTable();
                                            }
                                        }
                                    });

                                    window.addEventListener('mouseup', () => drag = false);
                                },

                                showTooltip: function(e, node) {
                                    const tt = document.getElementById('tooltip');
                                    if(!node) { tt.style.display='none'; return; }
                                    const stats = (App.selection.active ? App.calcSelectionStatsRevised() : App.globalStats).get(node.path) || {avg:0,max:0};
                                    tt.style.display = 'block';
                                    tt.style.left = (e.clientX+15)+'px'; tt.style.top = (e.clientY+15)+'px';
                                    tt.innerHTML = `<div class="tt-head">${node.name}</div>
                                        <div class="tt-row"><span>Range:</span> <span>${App.fmt(node.end-node.start)}</span></div>
                                        <div class="tt-row"><span>Avg:</span> <span>${App.fmt(stats.avg)}</span></div>
                                        <div class="tt-row"><span>Max:</span> <span>${App.fmt(stats.max)}</span></div>`;
                                },
                                fmt: function(v) {
                                    if(App.unit==='ms') return (v/1e6).toFixed(2)+'ms';
                                    if(App.unit==='us') return (v/1e3).toFixed(0)+'μs';
                                    return Math.round(v)+'ns';
                                },
                                getColor: function(str) {
                                    if(!App.colors[str]) {
                                        let h=0; for(let i=0;i<str.length;i++) h=str.charCodeAt(i)+((h<<5)-h);
                                        const isJelly = App.theme === 'jelly';
                                        const s = isJelly ? 90 : 65; const l = isJelly ? 60 : 55;
                                        App.colors[str] = `hsl(${Math.abs(h%360)}, ${s}%, ${l}%)`;
                                    }
                                    return App.colors[str];
                                },
                                adjustAlpha: function(hsl, a) { return hsl.replace(')', `, ${a})`).replace('hsl','hsla'); },
                                adjustLight: function(hsl, d) {
                                    const p = hsl.match(/\\d+/g); if(!p) return hsl;
                                    return `hsl(${p[0]}, ${p[1]}%, ${Math.min(100, parseInt(p[2])+d)}%)`;
                                }
                            };
                            function roundRect(ctx, x, y, w, h, r) {
                                if(w<2*r) r=w/2; if(h<2*r) r=h/2;
                                ctx.beginPath(); ctx.moveTo(x+r,y);
                                ctx.arcTo(x+w,y,x+w,y+h,r); ctx.arcTo(x+w,y+h,x,y+h,r);
                                ctx.arcTo(x,y+h,x,y,r); ctx.arcTo(x,y,x+w,y,r); ctx.closePath();
                            }
                            window.onload = App.init;
                        </script>
                    </body>
                    </html>
                """;
    }
}