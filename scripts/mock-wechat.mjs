import http from "node:http";
import crypto from "node:crypto";

const port = Number(process.env.MOCK_WECHAT_PORT || 4010);
const server = http.createServer((request, response) => {
  const url = new URL(request.url, `http://localhost:${port}`);
  if (url.pathname !== "/sns/jscode2session") {
    response.writeHead(404).end();
    return;
  }
  const code = url.searchParams.get("js_code") || url.searchParams.get("code");
  response.writeHead(200, { "Content-Type": "application/json" });
  if (!code || code === "invalid") {
    response.end(JSON.stringify({ errcode: 40029, errmsg: "invalid code" }));
    return;
  }
  const openid = `mock-${crypto.createHash("sha256").update(code).digest("hex").slice(0, 24)}`;
  response.end(JSON.stringify({ openid, session_key: "disposable-test-value" }));
});

server.listen(port, "127.0.0.1", () => {
  console.log(`Mock WeChat listening on 127.0.0.1:${port}`);
});
