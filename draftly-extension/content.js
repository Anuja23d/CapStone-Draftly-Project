function getReplyBox() {
    // Gmail reply/compose editor is a contenteditable textbox.
    const boxes = Array.from(document.querySelectorAll('div[role="textbox"][contenteditable="true"]'));
    // Prefer visible one (Gmail often keeps hidden compose boxes in DOM)
    return boxes.find((b) => b.offsetParent !== null) || null;
}

function getOpenedEmailText() {
    // In conversation view, message bodies typically use this class.
    // We pick the last visible body (most recent message in the thread).
    const bodies = Array.from(document.querySelectorAll("div.a3s.aiL")).filter(
        (el) => el.offsetParent !== null
    );
    const last = bodies[bodies.length - 1];
    const text = last ? last.innerText.trim() : "";
    return text;
}

function extractSignatureFromReplyBox(replyBoxText) {
    if (!replyBoxText) return "";
    const text = replyBoxText.trim();
    if (!text) return "";

    // Common signature delimiter
    const sigIndex = text.lastIndexOf("\n--");
    if (sigIndex !== -1) {
        const sig = text.slice(sigIndex).trim();
        // Avoid grabbing gigantic quoted content
        if (sig.length <= 800) return sig;
    }

    // Fallback: take last few non-empty lines (often the signature block)
    const lines = text.split("\n").map((l) => l.trim()).filter(Boolean);
    if (lines.length === 0) return "";
    const tail = lines.slice(Math.max(0, lines.length - 6)).join("\n").trim();
    return tail.length <= 800 ? tail : "";
}

function ensureSignature(draft, signature) {
    const d = (draft || "").trimEnd();
    const s = (signature || "").trim();
    if (!s) return d;

    // If already present, don't duplicate
    if (d.includes(s)) return d;

    return `${d}\n\n${s}`;
}

function sanitizeDraftText(text) {
    if (!text) return "";
    const t = String(text).replaceAll("Generating AI reply...", "");
    // Remove any "name,phone,email" one-liner that sometimes leaks into drafts.
    return t
        .replace(/^\s*[^,\n]{2,}\s*,\s*\+?\d[\d\s\-()]{6,}\s*,\s*[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\s*$/gim, "")
        .replace(/\n{3,}/g, "\n\n")
        .trim();
}

function getGmailSignatureText(replyBoxEl) {
    if (!replyBoxEl) return "";
    // Gmail signatures can appear in different wrappers depending on UI/experiments.
    // Try a few common patterns, first within the reply editor, then within the closest compose container.
    const composeRoot =
        replyBoxEl.closest('div[role="dialog"]') ||
        replyBoxEl.closest("div.nH") ||
        replyBoxEl.parentElement;

    const selectors = [
        ".gmail_signature",
        'div[data-smartmail="gmail_signature"]',
        'div[aria-label="Signature"]',
        'div[class*="gmail_signature"]'
    ];

    for (const sel of selectors) {
        const el = replyBoxEl.querySelector(sel) || (composeRoot ? composeRoot.querySelector(sel) : null);
        const txt = el ? (el.innerText || el.textContent || "").trim() : "";
        if (txt) return txt;
    }

    // Fallback: attempt to extract from the current reply box text itself.
    return extractSignatureFromReplyBox(replyBoxEl.innerText || "");
}

function injectButton() {
    const replyBox = getReplyBox();
    if (!replyBox) {
        // Don't inject on list pages (Inbox/Sent lists) where no reply editor exists yet.
        const existing = document.getElementById("draftly-btn");
        if (existing) existing.remove();
        return;
    }

    if (document.getElementById("draftly-btn")) return;

    const button = document.createElement("button");
    button.id = "draftly-btn";
    button.type = "button";
    button.innerText = "Generate Draft";
    button.style.marginTop = "8px";
    button.style.padding = "6px 10px";
    button.style.background = "#1a73e8";
    button.style.color = "white";
    button.style.border = "none";
    button.style.borderRadius = "6px";
    button.style.cursor = "pointer";

    button.onclick = generateDraft;

    // Place it near the reply box so it only shows in an opened email reply context.
    replyBox.parentElement.appendChild(button);
}

// Observe DOM changes instead of polling blindly (Gmail is SPA).
const observer = new MutationObserver(() => injectButton());
observer.observe(document.body, { childList: true, subtree: true });
injectButton();

function storageGet(key) {
    return new Promise((resolve) => {
        chrome.storage.local.get([key], (result) => resolve(result[key]));
    });
}

function storageSet(obj) {
    return new Promise((resolve) => {
        chrome.storage.local.set(obj, () => resolve());
    });
}

async function getConfiguredEmail() {
    let email = await storageGet("draftlyUserEmail");
    if (email && typeof email === "string" && email.trim()) return email.trim();

    email = prompt("Enter your Gmail address used to login to Draftly (OAuth):");
    if (!email || !email.trim()) return "";
    email = email.trim();
    await storageSet({ draftlyUserEmail: email });
    return email;
}

async function getSavedSignature(email) {
    // Prefer backend as source of truth
    try {
        const res = await fetch(`http://localhost:8080/users/signature?email=${encodeURIComponent(email)}`, {
            method: "GET"
        });
        if (res.ok) {
            const data = await res.json();
            const sig = (data?.signature || "").trim();
            if (sig) {
                await storageSet({ draftlySignature: sig });
                return sig;
            }
        }
    } catch (e) {
        // ignore and fall back to local cache
    }

    const local = await storageGet("draftlySignature");
    return (local && typeof local === "string") ? local.trim() : "";
}

async function generateDraft() {

    const emailBox = getReplyBox();

    if (!emailBox) {
        alert("Reply box not found. Open an Inbox email and click Reply first.");
        return;
    }

    // If an earlier run left a loading marker in the editor, remove it.
    if ((emailBox.innerText || "").includes("Generating AI reply...")) {
        emailBox.innerText = (emailBox.innerText || "").replaceAll("Generating AI reply...", "").trim();
    }

    const generateBtn = document.getElementById("draftly-btn");
    const prevBtnText = generateBtn ? generateBtn.innerText : "";
    if (generateBtn) {
        generateBtn.disabled = true;
        generateBtn.innerText = "Generating...";
        generateBtn.style.opacity = "0.8";
        generateBtn.style.cursor = "not-allowed";
    }

    // Generate reply ONLY from the opened email content (avoid picking up anything from the editor).
    const emailContent = getOpenedEmailText();
    if (!emailContent.trim()) {
        alert("Couldn't read the opened email content. Try opening an email thread first.");
        if (generateBtn) {
            generateBtn.disabled = false;
            generateBtn.innerText = prevBtnText || "Generate Draft";
            generateBtn.style.opacity = "1";
            generateBtn.style.cursor = "pointer";
        }
        return;
    }

    // Preserve Gmail's auto-inserted signature (name/phone/email) from the reply editor.
    const signature =
        getGmailSignatureText(emailBox) ||
        extractSignatureFromReplyBox(emailBox.innerText || "");

    // Do NOT overwrite the editor content with a loading message.
    const originalEditorText = emailBox.innerText || "";

    try {
        const email = await getConfiguredEmail();
        if (!email) {
            alert("Email is required.");
            return;
        }
        // Note: backend endpoint is /drafts/draft/generate
        const response = await fetch("http://localhost:8080/drafts/draft/generate", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            // Backend uses this email to look up stored Google OAuth tokens + saved signature.
            body: JSON.stringify({ email, body: emailContent })
        });

        if (!response.ok) {
            const text = await response.text();
            throw new Error(`API failed (${response.status}): ${text}`);
        }

        const data = await response.json();
        const backendOrLocalSignature = await getSavedSignature(email);
        // 1) Keep Gmail-editor signature if present; 2) otherwise use saved signature from backend/local
        const finalSig = (signature && signature.trim()) ? signature : backendOrLocalSignature;
        const draft = sanitizeDraftText(ensureSignature(data.generatedText, finalSig));

        showUI(draft, emailBox);

    } catch (err) {
        console.error(err);
        // Restore editor to what it was before generation attempt.
        emailBox.innerText = originalEditorText;
        alert(err?.message || "Error generating draft");
    } finally {
        if (generateBtn) {
            generateBtn.disabled = false;
            generateBtn.innerText = prevBtnText || "Generate Draft";
            generateBtn.style.opacity = "1";
            generateBtn.style.cursor = "pointer";
        }
    }
}

function showUI(draft, emailBox) {

    const old = document.getElementById("draftly-ui");
    if (old) old.remove();

    const container = document.createElement("div");
    container.id = "draftly-ui";

    const textarea = document.createElement("textarea");
    textarea.value = sanitizeDraftText(draft);
    textarea.style.width = "100%";
    textarea.style.height = "120px";

    const approve = document.createElement("button");
    approve.innerText = "Approve";

    const reject = document.createElement("button");
    reject.innerText = "Reject";

    approve.onclick = () => {
        emailBox.innerText = sanitizeDraftText(textarea.value);
        container.remove();
    };

    reject.onclick = () => {
        container.remove();
    };

    container.appendChild(textarea);
    container.appendChild(approve);
    container.appendChild(reject);

    emailBox.parentElement.appendChild(container);
}
