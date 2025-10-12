import { useState } from "react";
import API from "../api/api";

export default function Dashboard() {
  const [file, setFile] = useState(null);
  const [language, setLanguage] = useState("python");
  const [sandboxId, setSandboxId] = useState(null);

  const handleSandboxCreate = async (e) => {
    e.preventDefault();
    if (!file) return alert("Upload a file first!");

    const form = new FormData();
    form.append("language", language);
    form.append("code", file, file.name);

    try {
      const res = await API.post("/sandbox/create", form, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setSandboxId(res.data.sandboxId);
      alert("Sandbox created: " + res.data.sandboxId);
    } catch (err) {
      console.error(err);
      alert("Sandbox creation failed: " + (err.response?.data || err.message));
    }
  };

  return (
    <div>
      <section className="hero">
        <div className="hero-inner">
          <h1>Welcome to CollabDebug</h1>
          <p className="subtitle">Run code in isolated sandboxes, share sessions, and inspect logs — fast.</p>

          <div className="featured-row">
            <div className="featured-card">
              <div className="thumb">Sandbox</div>
              <h3 style={{margin:'8px 0'}}>Create a Quick Sandbox</h3>
              <p style={{color:'rgba(255,255,255,0.9)'}}>Upload code and run in an isolated container. Supports Python, Java, Node.</p>
              <div className="meta" style={{marginTop:12}}>
                <small>by you</small>
                <small>{sandboxId ? sandboxId.slice(0,6) : '—'}</small>
              </div>
            </div>

            <div style={{flex:1}}>
              <div className="card">
                <h2>Create Sandbox</h2>
                <form onSubmit={handleSandboxCreate} className="sandbox-form">
                  <div className="form-row">
                    <label>Language:</label>
                    <select value={language} onChange={(e) => setLanguage(e.target.value)}>
                      <option value="python">Python</option>
                      <option value="java">Java</option>
                      <option value="node">Node</option>
                    </select>
                  </div>
                  <div className="form-row">
                    <input type="file" onChange={(e) => setFile(e.target.files[0])} />
                  </div>
                  <button type="submit" className="btn btn-success">Create Sandbox</button>
                </form>
              </div>
            </div>
          </div>

          <div className="tiles">
            <div className="tile">
              <div className="tile-title">Start Sandbox</div>
              <div className="tile-sub">Create and run a snippet</div>
            </div>
            <div className="tile">
              <div className="tile-title">Sessions</div>
              <div className="tile-sub">Your recent debug sessions</div>
            </div>
            <div className="tile">
              <div className="tile-title">Docs</div>
              <div className="tile-sub">API & usage</div>
            </div>
            <div className="tile">
              <div className="tile-title">Community</div>
              <div className="tile-sub">Share and collaborate</div>
            </div>
          </div>
        </div>
      </section>
    </div>
  );
}
