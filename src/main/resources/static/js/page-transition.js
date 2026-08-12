const loginPage = document.querySelector(".login-page");
const loginForm = document.querySelector(".login-form");
const loginError = document.querySelector("#login-error");
const dashboardPage = document.querySelector(".dashboard-page");
const logoutForm = document.querySelector(".logout-form");

const forceReflow = (element) => {
    element.getBoundingClientRect();
};

if (loginPage?.classList.contains("is-returning")) {
    const clearReturnState = () => {
        loginPage.classList.remove("is-returning", "is-entered");
        if (window.location.search.includes("returning")) {
            window.history.replaceState({}, "", window.location.pathname);
        }
    };

    const startReturnAnimation = () => {
        window.requestAnimationFrame(() => {
            forceReflow(loginPage);
            window.requestAnimationFrame(() => {
                loginPage.classList.add("is-entered");
                window.setTimeout(clearReturnState, 820);
            });
        });
    };

    if (document.readyState === "complete") {
        window.setTimeout(startReturnAnimation, 80);
    } else {
        window.addEventListener("load", () => {
            window.setTimeout(startReturnAnimation, 80);
        }, { once: true });
    }
}

if (loginPage && loginForm) {
    loginForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const submitButton = loginForm.querySelector("button[type='submit']");
        const successUrl = loginForm.dataset.successUrl || "/";
        const formData = new FormData(loginForm);
        const loginUrl = new URL(loginForm.action, window.location.origin);

        const isLoginFailureResponse = (response) => {
            if (!response.url) {
                return false;
            }

            const responseUrl = new URL(response.url, window.location.origin);
            return responseUrl.pathname === loginUrl.pathname && (response.redirected || responseUrl.search.includes("error"));
        };

        loginError?.classList.add("is-hidden");
        submitButton.disabled = true;
        submitButton.textContent = "Signing in...";

        try {
            const response = await fetch(loginForm.action, {
                method: "POST",
                body: formData,
                credentials: "same-origin",
                headers: {
                    "X-Requested-With": "XMLHttpRequest"
                }
            });

            if (!response.ok || isLoginFailureResponse(response)) {
                throw new Error("Login failed");
            }

            loginPage.classList.remove("is-returning", "is-entered");
            forceReflow(loginPage);
            loginPage.classList.add("is-leaving");
            window.setTimeout(() => {
                window.location.assign(successUrl);
            }, 760);
        } catch (error) {
            loginError?.classList.remove("is-hidden");
            submitButton.disabled = false;
            submitButton.textContent = "Login";
        }
    });
}

if (dashboardPage && logoutForm) {
    logoutForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const submitButton = logoutForm.querySelector("button[type='submit']");
        const loginUrl = logoutForm.dataset.loginUrl || "/login?returning=true";
        const formData = new FormData(logoutForm);

        submitButton.disabled = true;
        submitButton.textContent = "Signing out...";
        dashboardPage.classList.add("is-leaving");

        window.setTimeout(async () => {
            try {
                const response = await fetch(logoutForm.action, {
                    method: "POST",
                    body: formData,
                    credentials: "same-origin",
                    headers: {
                        "X-Requested-With": "XMLHttpRequest"
                    }
                });

                if (!response.ok) {
                    throw new Error("Logout failed");
                }

                window.location.assign(loginUrl);
            } catch (error) {
                dashboardPage.classList.remove("is-leaving");
                submitButton.disabled = false;
                submitButton.textContent = "Logout";
            }
        }, 420);
    });
}

const builderLayout = document.querySelector(".builder-layout");

if (builderLayout) {
    const envButtons = Array.from(document.querySelectorAll(".env-container"));
    const commandBlocks = Array.from(document.querySelectorAll(".command-block"));
    const envStage = document.querySelector("#env-stage");
    const stageEnvName = document.querySelector("#stage-env-name");
    const commandSlot = document.querySelector("#command-slot");
    const runButton = document.querySelector("#run-command");
    const resultOutput = document.querySelector("#result-output");

    let activeEnv = envButtons.find((button) => button.classList.contains("is-active"))?.dataset.env || "SIT";
    let mountedCommand = null;

    const escapeHtml = (value) => String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll('"', "&quot;")
            .replaceAll("'", "&#039;");

    const getCommandTone = (commandBlock) => Array.from(commandBlock.classList)
            .find((className) => className.startsWith("is-")) || "is-blue";

    const getCommandFromBlock = (commandBlock) => ({
        id: commandBlock.dataset.command,
        title: commandBlock.dataset.title,
        fieldLabel: commandBlock.dataset.fieldLabel,
        fieldValue: commandBlock.dataset.fieldValue,
        tone: getCommandTone(commandBlock)
    });

    const commandsById = new Map(commandBlocks.map((commandBlock) => [
        commandBlock.dataset.command,
        getCommandFromBlock(commandBlock)
    ]));

    const clearResult = () => {
        if (!resultOutput) {
            return;
        }

        resultOutput.classList.add("is-empty");
        resultOutput.textContent = "No execution yet.";
    };

    const updateRunButton = () => {
        if (runButton) {
            runButton.disabled = !mountedCommand;
        }
    };

    const updateEnvironment = (envName) => {
        activeEnv = envName;

        envButtons.forEach((button) => {
            button.classList.toggle("is-active", button.dataset.env === activeEnv);
        });

        if (stageEnvName) {
            stageEnvName.textContent = activeEnv;
        }

        updateRunButton();
        clearResult();
    };

    const renderMountedCommand = () => {
        if (!commandSlot || !mountedCommand) {
            return;
        }

        commandSlot.innerHTML = `
            <div class="mounted-command ${escapeHtml(mountedCommand.tone)}">
                <span>${escapeHtml(mountedCommand.title)}</span>
                <label class="mounted-input-label">
                    ${escapeHtml(mountedCommand.fieldLabel)}
                    <input class="mounted-command-input" type="text" value="${escapeHtml(mountedCommand.fieldValue)}" aria-label="${escapeHtml(mountedCommand.fieldLabel)}">
                </label>
            </div>
        `;
    };

    const mountCommand = (command) => {
        mountedCommand = command;
        renderMountedCommand();
        updateRunButton();
        clearResult();
    };

    const buildTable = (headers, rows) => `
        <table class="result-table">
            <thead>
                <tr>${headers.map((header) => `<th>${escapeHtml(header)}</th>`).join("")}</tr>
            </thead>
            <tbody>
                ${rows.map((row) => `<tr>${row.map((cell) => `<td>${escapeHtml(cell)}</td>`).join("")}</tr>`).join("")}
            </tbody>
        </table>
    `;

    const buildResult = (command, value) => {
        if (command.id === "customer") {
            return buildTable(
                    ["customer_id", "customer_name", "status", "environment"],
                    [[value || "101", "Ahmed", "Active", activeEnv]]
            );
        }

        if (command.id === "orders") {
            return buildTable(
                    ["order_status", "order_count", "environment"],
                    [[value || "ALL", activeEnv === "SIT" ? "42" : "39", activeEnv]]
            );
        }

        return buildTable(
                ["username", "last_command", "status", "environment"],
                [[value || "qa_user", command.title, "SUCCESS", activeEnv]]
        );
    };

    envButtons.forEach((button) => {
        button.addEventListener("click", () => {
            updateEnvironment(button.dataset.env);
        });

        button.addEventListener("dragover", (event) => {
            event.preventDefault();
            button.classList.add("is-drag-over");
        });

        button.addEventListener("dragleave", () => {
            button.classList.remove("is-drag-over");
        });

        button.addEventListener("drop", (event) => {
            event.preventDefault();
            button.classList.remove("is-drag-over");
            updateEnvironment(button.dataset.env);

            const command = commandsById.get(event.dataTransfer.getData("text/plain"));
            if (command) {
                mountCommand(command);
            }
        });
    });

    commandBlocks.forEach((commandBlock) => {
        commandBlock.addEventListener("click", () => {
            mountCommand(getCommandFromBlock(commandBlock));
        });

        commandBlock.addEventListener("dragstart", (event) => {
            event.dataTransfer.setData("text/plain", commandBlock.dataset.command);
            event.dataTransfer.effectAllowed = "copy";
            commandBlock.classList.add("is-dragging");
        });

        commandBlock.addEventListener("dragend", () => {
            commandBlock.classList.remove("is-dragging");
        });
    });

    envStage?.addEventListener("dragover", (event) => {
        event.preventDefault();
        envStage.classList.add("is-drag-over");
    });

    envStage?.addEventListener("dragleave", () => {
        envStage.classList.remove("is-drag-over");
    });

    envStage?.addEventListener("drop", (event) => {
        event.preventDefault();
        envStage.classList.remove("is-drag-over");

        const command = commandsById.get(event.dataTransfer.getData("text/plain"));
        if (command) {
            mountCommand(command);
        }
    });

    runButton?.addEventListener("click", () => {
        if (!mountedCommand || !resultOutput) {
            return;
        }

        const inputValue = document.querySelector(".mounted-command-input")?.value.trim() || mountedCommand.fieldValue;

        resultOutput.classList.remove("is-empty");
        resultOutput.innerHTML = `
            <div class="result-status">
                <span>SUCCESS</span>
                <span>${escapeHtml(activeEnv)}</span>
            </div>
            ${buildResult(mountedCommand, inputValue)}
        `;
    });

    updateEnvironment(activeEnv);
}
