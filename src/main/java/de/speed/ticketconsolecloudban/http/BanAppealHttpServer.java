package de.speed.ticketconsolecloudban.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import de.speed.ticketconsolecloudban.appeal.BanAppealService;
import de.speed.ticketconsolecloudban.config.PanelConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BanAppealHttpServer {

  private static final Logger LOGGER = LoggerFactory.getLogger(BanAppealHttpServer.class);

  private final PanelConfiguration configuration;
  private final BanAppealService appealService;

  private HttpServer server;
  private ExecutorService executor;

  public BanAppealHttpServer(PanelConfiguration configuration, BanAppealService appealService) {
    this.configuration = configuration;
    this.appealService = appealService;
  }

  public void start() {
    if (!this.configuration.appealEnabled() || this.server != null) {
      return;
    }

    try {
      this.server = HttpServer.create(
        new InetSocketAddress(this.configuration.appealBindHost(), this.configuration.appealBindPort()),
        0);
      this.executor = Executors.newVirtualThreadPerTaskExecutor();
      this.server.setExecutor(this.executor);
      this.server.createContext("/", this::handleRequest);
      this.server.start();
    } catch (IOException exception) {
      throw new IllegalStateException("Der Entbannungsantrag-HTTP-Server konnte nicht gestartet werden.", exception);
    }
  }

  public void stop() {
    if (this.server != null) {
      this.server.stop(0);
      this.server = null;
    }
    if (this.executor != null) {
      this.executor.shutdownNow();
      this.executor = null;
    }
  }

  private void handleRequest(HttpExchange exchange) throws IOException {
    HttpExchangeUtils.allowCommonHeaders(exchange.getResponseHeaders());

    if (HttpExchangeUtils.matchesMethod(exchange, "OPTIONS")) {
      HttpExchangeUtils.sendNoContent(exchange);
      return;
    }

    try {
      var segments = HttpExchangeUtils.pathSegments(exchange);
      if (segments.size() == 2 && "api".equals(segments.get(0)) && "appeals".equals(segments.get(1))) {
        this.handleAppealApi(exchange);
        return;
      }
      if (segments.size() == 3
        && "api".equals(segments.get(0))
        && "appeals".equals(segments.get(1))
        && "meta".equals(segments.get(2))
        && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        HttpExchangeUtils.writeJson(exchange, 200, this.appealService.meta());
        return;
      }
      if (segments.size() == 3
        && "api".equals(segments.get(0))
        && "appeals".equals(segments.get(1))
        && "status".equals(segments.get(2))
        && HttpExchangeUtils.matchesMethod(exchange, "GET")) {
        var query = HttpExchangeUtils.queryParameters(exchange);
        HttpExchangeUtils.writeJson(exchange, 200, this.appealService.status(query.get("token")));
        return;
      }
      if (segments.isEmpty() || (segments.size() == 1 && ("status".equals(segments.get(0)) || "app.js".equals(segments.get(0))))) {
        this.writeAppealPage(exchange);
        return;
      }

      HttpExchangeUtils.writeText(exchange, 404, "Nicht gefunden", "text/plain; charset=utf-8");
    } catch (IllegalArgumentException exception) {
      HttpExchangeUtils.writeJson(exchange, 400, new HttpExchangeUtils.ApiError(exception.getMessage()));
    } catch (Exception exception) {
      LOGGER.error("Unbehandelter Fehler im Entbannungsantrag-HTTP-Handler", exception);
      HttpExchangeUtils.writeJson(exchange, 500, new HttpExchangeUtils.ApiError("Interner Serverfehler"));
    }
  }

  private void handleAppealApi(HttpExchange exchange) throws IOException {
    if (!HttpExchangeUtils.matchesMethod(exchange, "POST")) {
      HttpExchangeUtils.writeJson(exchange, 405, new HttpExchangeUtils.ApiError("Methode nicht erlaubt"));
      return;
    }

    var maxRequestBytes = (this.configuration.appealMaxFileBytes() * Math.max(1, this.configuration.appealMaxFiles())) + 512_000L;
    var body = exchange.getRequestBody().readNBytes((int) Math.min(maxRequestBytes + 1, Integer.MAX_VALUE));
    if (body.length > maxRequestBytes) {
      HttpExchangeUtils.writeJson(exchange, 413, new HttpExchangeUtils.ApiError("Upload ist zu gross."));
      return;
    }

    var form = MultipartFormParser.parse(exchange.getRequestHeaders().getFirst("Content-Type"), body);
    HttpExchangeUtils.writeJson(exchange, 201, this.appealService.submit(form));
  }

  private void writeAppealPage(HttpExchange exchange) throws IOException {
    HttpExchangeUtils.writeText(exchange, 200, page(), "text/html; charset=utf-8");
  }

  private static String page() {
    return """
      <!DOCTYPE html>
      <html lang="de">
      <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1.0">
        <title>Entbannungsantrag</title>
        <style>
          :root{--bg:#07131d;--bg2:#081019;--card:#0f1e2b;--card2:#08111a;--line:rgba(244,188,70,.26);--text:#f5f0e7;--muted:#9eb0bc;--accent:#f4bc46;--accent2:#ff9f43;--green:#46c4a6;--danger:#ff7268;--success:#7cd7b0;--radius:28px}
          *{box-sizing:border-box}body{margin:0;min-height:100vh;background:radial-gradient(circle at top left,rgba(244,188,70,.18),transparent 34%),radial-gradient(circle at bottom right,rgba(70,196,166,.14),transparent 32%),linear-gradient(180deg,var(--bg),var(--bg2));color:var(--text);font-family:Bahnschrift,"Segoe UI Variable Text","Trebuchet MS",sans-serif;overflow-x:hidden}
          body:before{content:"";position:fixed;inset:0;background-image:linear-gradient(rgba(255,255,255,.035) 1px,transparent 1px),linear-gradient(90deg,rgba(255,255,255,.035) 1px,transparent 1px);background-size:34px 34px;mask-image:linear-gradient(180deg,rgba(0,0,0,.72),transparent 92%);pointer-events:none}
          main{position:relative;width:min(980px,calc(100vw - 28px));margin:0 auto;padding:34px 0 50px}.topbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin-bottom:18px;padding:12px 14px;border:1px solid var(--line);border-radius:22px;background:rgba(6,12,18,.72);box-shadow:0 18px 70px rgba(0,0,0,.32);backdrop-filter:blur(18px)}.brand{display:flex;align-items:center;gap:12px;font-weight:900}.brand img{width:42px;height:42px;object-fit:contain;border-radius:12px;filter:drop-shadow(0 10px 22px rgba(0,0,0,.32))}.brand span{font-size:1.05rem}.pill{color:#1d1406;background:linear-gradient(135deg,var(--accent),var(--accent2));border-radius:999px;padding:9px 12px;font-weight:900}
          .card{position:relative;overflow:hidden;border:1px solid var(--line);border-radius:var(--radius);background:linear-gradient(180deg,rgba(15,30,43,.94),rgba(8,17,26,.97));box-shadow:0 24px 80px rgba(0,0,0,.38);padding:30px;animation:rise .5s ease both}.card:before{content:"";position:absolute;inset:0 0 auto;height:5px;background:linear-gradient(90deg,var(--accent),var(--accent2),var(--green))}
          .eyebrow{margin:0 0 8px;color:var(--accent);letter-spacing:.22em;text-transform:uppercase;font-size:.74rem;font-weight:900}h1{margin:0 0 12px;font-size:clamp(2.1rem,5vw,4rem);line-height:.95}.muted{color:var(--muted);line-height:1.55}form{display:grid;gap:16px;margin-top:24px}.grid{display:grid;grid-template-columns:1fr 1fr;gap:14px}label{display:grid;gap:8px}input,textarea,button{border:1px solid rgba(255,255,255,.1);border-radius:14px;padding:13px 14px;background:rgba(6,11,17,.74);color:var(--text);font:inherit;outline:none}input:focus,textarea:focus{border-color:rgba(244,188,70,.48);background:rgba(10,18,27,.95)}textarea{min-height:150px;resize:vertical}.full{grid-column:1/-1}button{cursor:pointer;border:0;background:linear-gradient(135deg,var(--accent),var(--accent2));color:#1d1406;font-weight:900}.status{margin-top:16px}.error{color:var(--danger)}.success{color:var(--success)}.hidden{display:none!important}.status-box{display:grid;gap:12px;margin-top:20px;padding:20px;border-radius:20px;background:rgba(255,255,255,.045);border:1px solid rgba(244,188,70,.18)}.status-box strong{font-size:1.1rem}.status-message{color:#d7e2ea;line-height:1.55}.meta-row{display:grid;gap:5px;padding-top:10px;border-top:1px solid rgba(255,255,255,.08)}
          @keyframes rise{from{opacity:0;transform:translateY(12px)}to{opacity:1;transform:translateY(0)}}@media(max-width:720px){main{width:min(100vw - 20px,980px);padding-top:14px}.topbar{align-items:flex-start;flex-direction:column}.grid{grid-template-columns:1fr}.card{padding:22px}}
        </style>
      </head>
      <body>
        <main>
          <nav class="topbar" aria-label="Craftplay">
            <div class="brand"><img id="brand-logo" class="hidden" alt=""><span id="brand-name">Craftplay.de</span></div>
            <span class="pill">craftplay.de</span>
          </nav>
          <section class="card" id="form-card">
            <p class="eyebrow">Ban Appeal</p>
            <h1>Entbannungsantrag</h1>
            <p class="muted">Gib die Random Ban-ID aus deiner Ban-Nachricht und deinen Spielernamen ein. Beides muss zusammenpassen.</p>
            <form id="appeal-form">
              <div class="grid">
                <label><span>Random Ban-ID</span><input name="banId" required placeholder="z.B. A7K9Q2"></label>
                <label><span>Spielername</span><input name="playerName" required placeholder="Dein Minecraft Name"></label>
                <label><span>E-Mail</span><input name="email" type="email" required placeholder="name@example.com"></label>
                <label><span>Video-Link optional</span><input name="videoLink" type="url" placeholder="https://youtu.be/..."></label>
                <label class="full"><span>Begruendung</span><textarea name="reason" required placeholder="Warum soll dein Ban aufgehoben werden?"></textarea></label>
                <label class="full"><span>Beweise hochladen optional</span><input name="evidence" type="file" multiple></label>
              </div>
              <button type="submit">Antrag absenden</button>
            </form>
            <p id="form-status" class="status muted"></p>
          </section>
          <section class="card hidden" id="status-card">
            <p class="eyebrow">Status</p>
            <h1>Dein Antrag</h1>
            <div id="status-output" class="status-box muted">Status wird geladen.</div>
          </section>
        </main>
        <script>
          const formCard=document.getElementById('form-card');const statusCard=document.getElementById('status-card');const form=document.getElementById('appeal-form');const formStatus=document.getElementById('form-status');const output=document.getElementById('status-output');const brandName=document.getElementById('brand-name');const brandLogo=document.getElementById('brand-logo');
          const params=new URLSearchParams(location.search);const token=params.get('token');
          loadMeta();
          if(token){formCard.classList.add('hidden');statusCard.classList.remove('hidden');loadStatus(token);}
          form.addEventListener('submit',async event=>{event.preventDefault();formStatus.textContent='Antrag wird gesendet...';formStatus.className='status muted';try{const response=await fetch('/api/appeals',{method:'POST',body:new FormData(form)});const data=await response.json().catch(()=>({}));if(!response.ok)throw new Error(data.error||'Antrag konnte nicht gesendet werden.');form.reset();formStatus.textContent=data.message||'Antrag wurde eingereicht.';formStatus.className='status success';if(data.statusUrl){history.replaceState(null,'',data.statusUrl);}}catch(error){formStatus.textContent=error.message;formStatus.className='status error';}});
          async function loadMeta(){try{const response=await fetch('/api/appeals/meta');const data=await response.json().catch(()=>({}));if(data.brandName){brandName.textContent=data.brandName;document.title='Entbannungsantrag - '+data.brandName;}if(data.brandLogoUrl){brandLogo.src=data.brandLogoUrl;brandLogo.classList.remove('hidden');}}catch(error){}}
          async function loadStatus(token){try{const response=await fetch('/api/appeals/status?token='+encodeURIComponent(token));const data=await response.json().catch(()=>({}));if(!response.ok)throw new Error(data.error||'Status konnte nicht geladen werden.');output.innerHTML='<strong>Status: '+escapeHtml(data.status||'-')+'</strong><span class="status-message">'+escapeHtml(data.statusText||'')+'</span><div class="meta-row"><span>Random Ban-ID: '+escapeHtml(data.publicBanId||'-')+'</span><span>Spieler: '+escapeHtml(data.playerName||'-')+'</span><span>Eingereicht: '+formatDate(data.createdAt)+'</span></div>'+(data.teamNote?'<span>Team-Notiz: '+escapeHtml(data.teamNote)+'</span>':'');}catch(error){output.textContent=error.message;output.className='status-box error';}}
          function formatDate(value){const time=Date.parse(value||'');if(Number.isNaN(time))return escapeHtml(value||'-');return new Intl.DateTimeFormat('de-DE',{dateStyle:'medium',timeStyle:'short'}).format(new Date(time));}
          function escapeHtml(value){return String(value??'').replaceAll('&','&amp;').replaceAll('<','&lt;').replaceAll('>','&gt;').replaceAll('"','&quot;').replaceAll("'","&#39;");}
        </script>
      </body>
      </html>
      """;
  }
}
