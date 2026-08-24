const loginPage = document.querySelector(".login-page");
const loginForm = document.querySelector(".login-form");
const loginError = document.querySelector("#login-error");
const dashboardPage = document.querySelector(".dashboard-page");
const logoutForm = document.querySelector(".logout-form");
const LOGIN_RETURN_DURATION = 980;
const DASHBOARD_EXIT_DURATION = 760;
const ASYNC_HEADERS = {
    "X-Requested-With": "XMLHttpRequest"
};

const forceReflow = (element) => {
    element.getBoundingClientRect();
};

const startPageExit = (page) => {
    forceReflow(page);
    window.requestAnimationFrame(() => page.classList.add("is-leaving"));
};

const postForm = (form) => fetch(form.action, {
    method: "POST",
    body: new FormData(form),
    credentials: "same-origin",
    headers: ASYNC_HEADERS
});

const isLoginFailureResponse = (response, loginUrl) => {
    if (!response.url) {
        return false;
    }

    const responseUrl = new URL(response.url, window.location.origin);
    return responseUrl.pathname === loginUrl.pathname && (response.redirected || responseUrl.search.includes("error"));
};

const setButtonState = (button, text, disabled) => {
    button.disabled = disabled;
    button.textContent = text;
};

const setIconButtonState = (button, label, disabled) => {
    button.disabled = disabled;
    button.title = label;
    button.setAttribute("aria-label", label);
};

const waitForLoginVisual = async () => {
    const visualImage = loginPage?.querySelector(".login-visual-layer img");

    if (!visualImage || typeof visualImage.decode !== "function") {
        return;
    }

    try {
        await visualImage.decode();
    } catch (error) {
        // The transition can still run if decoding is unsupported or interrupted.
    }
};

if (loginPage?.classList.contains("is-returning")) {
    const clearReturnState = () => {
        loginPage.classList.remove("is-returning", "is-entered");
        if (window.location.search.includes("returning")) {
            window.history.replaceState({}, "", window.location.pathname);
        }
    };

    const startReturnAnimation = async () => {
        await waitForLoginVisual();
        window.requestAnimationFrame(() => {
            forceReflow(loginPage);
            window.requestAnimationFrame(() => {
                loginPage.classList.add("is-entered");
                window.setTimeout(clearReturnState, LOGIN_RETURN_DURATION);
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
        if (event.submitter?.dataset.nativeSubmit === "true") {
            return;
        }

        event.preventDefault();

        const submitButton = event.submitter || loginForm.querySelector("button[type='submit']");
        const successUrl = loginForm.dataset.successUrl || "/";
        const loginUrl = new URL(loginForm.action, window.location.origin);

        loginError?.classList.add("is-hidden");
        setButtonState(submitButton, "Signing in...", true);

        try {
            const response = await postForm(loginForm);

            if (!response.ok || isLoginFailureResponse(response, loginUrl)) {
                throw new Error("Login failed");
            }

            loginPage.classList.remove("is-returning", "is-entered");
            startPageExit(loginPage);
            window.setTimeout(() => {
                window.location.assign(successUrl);
            }, 760);
        } catch (error) {
            loginError?.classList.remove("is-hidden");
            setButtonState(submitButton, "Login", false);
        }
    });
}

if (dashboardPage && logoutForm) {
    logoutForm.addEventListener("submit", async (event) => {
        event.preventDefault();

        const submitButton = logoutForm.querySelector("button[type='submit']");
        const loginUrl = logoutForm.dataset.loginUrl || "/login?returning=true";

        setIconButtonState(submitButton, "Signing out...", true);
        startPageExit(dashboardPage);

        window.setTimeout(async () => {
            try {
                const response = await postForm(logoutForm);

                if (!response.ok) {
                    throw new Error("Logout failed");
                }

                window.location.assign(loginUrl);
            } catch (error) {
                dashboardPage.classList.remove("is-leaving");
                setIconButtonState(submitButton, "Logout", false);
            }
        }, DASHBOARD_EXIT_DURATION);
    });
}

const builderLayout = document.querySelector(".builder-layout");

if (builderLayout) {
    const envButtons = Array.from(document.querySelectorAll(".env-container"));
    const commandBlocks = Array.from(document.querySelectorAll(".command-block"));
    const workflowCanvas = document.querySelector("#workflow-canvas");
    const workflowEnvBlock = document.querySelector("#workflow-env-block");
    const workflowEnvName = document.querySelector("#workflow-env-name");
    const workflowEnvNote = document.querySelector("#workflow-env-note");
    const workflowCommandBlock = document.querySelector("#workflow-command-block");
    const workflowCommandTitle = document.querySelector("#workflow-command-title");
    const workflowCommandFieldLabel = document.querySelector("#workflow-command-field-label");
    const workflowCommandInput = document.querySelector("#workflow-command-input");
    const runButton = document.querySelector("#run-command");
    const resultOutput = document.querySelector("#result-output");

    const CONNECT_DISTANCE = 54;
    const DISCONNECT_DISTANCE = 92;
    const activeEnvironmentButton = envButtons.find((button) => button.classList.contains("is-active")) || envButtons[0];

    const getEnvironmentFromButton = (button) => ({
        id: button?.dataset.env || "",
        note: button?.dataset.envNote || ""
    });

    let activeEnvironment = getEnvironmentFromButton(activeEnvironmentButton);
    let activeCommand = null;
    let commandPosition = { x: 0, y: 0 };
    let dragState = null;

    const connectionState = {
        environmentId: null,
        commandId: null,
        connectionStatus: "disconnected"
    };

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

    const getConnectorSize = () => {
        const styles = window.getComputedStyle(workflowCanvas);
        return {
            width: parseFloat(styles.getPropertyValue("--connector-width")) || 38,
            height: parseFloat(styles.getPropertyValue("--connector-height")) || 58
        };
    };

    const clamp = (value, min, max) => Math.min(Math.max(value, min), max);

    const setConnectionState = (environmentId, commandId, status) => {
        connectionState.environmentId = environmentId;
        connectionState.commandId = commandId;
        connectionState.connectionStatus = status;
    };

    const setConnectionClasses = (connected) => {
        workflowCanvas?.classList.toggle("is-connected", connected);
        workflowCanvas?.classList.remove("is-connection-available");
        workflowCommandBlock?.classList.toggle("is-connected", connected);
        workflowCommandBlock?.classList.toggle("is-disconnected", !connected);
        workflowCommandBlock?.classList.remove("is-connection-available");
    };

    const markDisconnected = () => {
        setConnectionState(null, activeCommand?.id || null, "disconnected");
        setConnectionClasses(false);
    };

    const clearResult = () => {
        if (!resultOutput) {
            return;
        }

        resultOutput.classList.add("is-empty");
        resultOutput.textContent = "No execution yet.";
    };

    const updateRunButton = () => {
        if (runButton) {
            runButton.disabled = connectionState.connectionStatus !== "connected";
        }
    };

    const setCommandPosition = (x, y) => {
        if (!workflowCanvas || !workflowCommandBlock) {
            return;
        }

        const connector = getConnectorSize();
        const maxX = Math.max(connector.width + 8, workflowCanvas.clientWidth - workflowCommandBlock.offsetWidth - 10);
        const maxY = Math.max(8, workflowCanvas.clientHeight - workflowCommandBlock.offsetHeight - 8);

        commandPosition = {
            x: clamp(x, connector.width + 8, maxX),
            y: clamp(y, 8, maxY)
        };

        workflowCommandBlock.style.left = `${commandPosition.x}px`;
        workflowCommandBlock.style.top = `${commandPosition.y}px`;
    };

    const getDefaultCommandPosition = () => {
        if (!workflowCanvas || !workflowCommandBlock) {
            return { x: 430, y: 35 };
        }

        if (workflowCanvas.clientWidth < 620) {
            return {
                x: 92,
                y: 210
            };
        }

        return {
            x: Math.max(280, workflowCanvas.clientWidth - workflowCommandBlock.offsetWidth - 54),
            y: 35
        };
    };

    const getConnectionDelta = () => {
        const commandCenterY = commandPosition.y + (workflowCommandBlock.offsetHeight / 2);
        const envCenterY = workflowEnvBlock.offsetTop + (workflowEnvBlock.offsetHeight / 2);
        const envRight = workflowEnvBlock.offsetLeft + workflowEnvBlock.offsetWidth;

        return {
            x: commandPosition.x - envRight,
            y: commandCenterY - envCenterY
        };
    };

    const isWithinConnectionRange = (distance) => {
        const delta = getConnectionDelta();
        return Math.abs(delta.x) <= distance && Math.abs(delta.y) <= distance;
    };

    const setConnectionFeedback = (available) => {
        workflowCanvas?.classList.toggle("is-connection-available", available);
        workflowCommandBlock?.classList.toggle("is-connection-available", available);
    };

    const disconnectCommand = (moveToDefaultPosition) => {
        markDisconnected();

        if (moveToDefaultPosition) {
            const position = getDefaultCommandPosition();
            setCommandPosition(position.x, position.y);
        }

        updateRunButton();
        clearResult();
    };

    const snapCommand = () => {
        if (!activeCommand || !workflowEnvBlock || !workflowCommandBlock) {
            return;
        }

        const x = workflowEnvBlock.offsetLeft + workflowEnvBlock.offsetWidth;
        const y = workflowEnvBlock.offsetTop + ((workflowEnvBlock.offsetHeight - workflowCommandBlock.offsetHeight) / 2);

        workflowCommandBlock.classList.add("is-snapping");
        setCommandPosition(x, y);
        setConnectionState(activeEnvironment.id, activeCommand.id, "connected");
        setConnectionClasses(true);
        window.setTimeout(() => workflowCommandBlock.classList.remove("is-snapping"), 190);
        updateRunButton();
        clearResult();
    };

    const updateEnvironment = (environment, autoConnect = false) => {
        activeEnvironment = environment;

        envButtons.forEach((button) => {
            button.classList.toggle("is-active", button.dataset.env === activeEnvironment.id);
        });

        if (workflowEnvBlock) {
            workflowEnvBlock.dataset.env = activeEnvironment.id;
        }

        if (workflowEnvName) {
            workflowEnvName.textContent = activeEnvironment.id;
        }

        if (workflowEnvNote) {
            workflowEnvNote.textContent = activeEnvironment.note;
        }

        if (autoConnect && activeCommand) {
            markDisconnected();
            snapCommand();
            return;
        }

        disconnectCommand(true);
    };

    const renderCommand = (command, autoConnect = false) => {
        if (!command || !workflowCommandBlock) {
            return;
        }

        activeCommand = command;
        workflowCommandBlock.dataset.command = command.id;
        workflowCommandBlock.className = `workflow-command-block ${command.tone} is-disconnected`;
        workflowCommandTitle.textContent = command.title;
        workflowCommandFieldLabel.textContent = command.fieldLabel;
        workflowCommandInput.value = command.fieldValue;
        workflowCommandInput.setAttribute("aria-label", command.fieldLabel);

        if (autoConnect) {
            snapCommand();
            return;
        }

        disconnectCommand(true);
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
        const commandName = command.title.toLowerCase();

        if (commandName.includes("customer")) {
            return buildTable(
                    ["customer_id", "customer_name", "status", "environment"],
                    [[value || "101", "Ahmed", "Active", connectionState.environmentId]]
            );
        }

        if (commandName.includes("order")) {
            return buildTable(
                    ["order_status", "order_count", "environment"],
                    [[value || "ALL", connectionState.environmentId === "SIT" ? "42" : "39", connectionState.environmentId]]
            );
        }

        if (commandName.includes("history") || commandName.includes("user")) {
            return buildTable(
                    ["username", "last_command", "status", "environment"],
                    [[value || "qa_user", command.title, "SUCCESS", connectionState.environmentId]]
            );
        }

        return buildTable(
                ["input", "command", "status", "environment"],
                [[value || "-", command.title, "SUCCESS", connectionState.environmentId]]
        );
    };

    envButtons.forEach((button) => {
        button.addEventListener("click", () => {
            updateEnvironment(getEnvironmentFromButton(button), true);
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
            updateEnvironment(getEnvironmentFromButton(button));
        });
    });

    commandBlocks.forEach((commandBlock) => {
        commandBlock.addEventListener("click", () => {
            renderCommand(commandsById.get(commandBlock.dataset.command), true);
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

    workflowCanvas?.addEventListener("dragover", (event) => {
        event.preventDefault();
    });

    workflowCanvas?.addEventListener("drop", (event) => {
        event.preventDefault();

        const command = commandsById.get(event.dataTransfer.getData("text/plain"));
        if (command) {
            renderCommand(command);
        }
    });

    workflowCommandBlock?.addEventListener("pointerdown", (event) => {
        if (event.target.closest("input")) {
            return;
        }

        event.preventDefault();
        workflowCommandBlock.setPointerCapture(event.pointerId);

        const canvasRect = workflowCanvas.getBoundingClientRect();
        dragState = {
            pointerId: event.pointerId,
            offsetX: event.clientX - canvasRect.left - commandPosition.x,
            offsetY: event.clientY - canvasRect.top - commandPosition.y
        };

        workflowCommandBlock.classList.remove("is-snapping");
        workflowCommandBlock.classList.add("is-dragging");
    });

    workflowCommandBlock?.addEventListener("pointermove", (event) => {
        if (!dragState || dragState.pointerId !== event.pointerId) {
            return;
        }

        const canvasRect = workflowCanvas.getBoundingClientRect();
        setCommandPosition(
                event.clientX - canvasRect.left - dragState.offsetX,
                event.clientY - canvasRect.top - dragState.offsetY
        );

        const nearConnection = isWithinConnectionRange(CONNECT_DISTANCE);
        setConnectionFeedback(nearConnection);

        if (connectionState.connectionStatus === "connected" && !isWithinConnectionRange(DISCONNECT_DISTANCE)) {
            disconnectCommand(false);
        }
    });

    workflowCommandBlock?.addEventListener("pointerup", (event) => {
        if (!dragState || dragState.pointerId !== event.pointerId) {
            return;
        }

        workflowCommandBlock.releasePointerCapture(event.pointerId);
        workflowCommandBlock.classList.remove("is-dragging");
        dragState = null;

        if (isWithinConnectionRange(CONNECT_DISTANCE)) {
            snapCommand();
            return;
        }

        disconnectCommand(false);
    });

    workflowCommandBlock?.addEventListener("pointercancel", () => {
        workflowCommandBlock.classList.remove("is-dragging");
        dragState = null;
        setConnectionFeedback(false);
    });

    runButton?.addEventListener("click", () => {
        if (!activeCommand || connectionState.connectionStatus !== "connected" || !resultOutput) {
            return;
        }

        const inputValue = workflowCommandInput?.value.trim() || activeCommand.fieldValue;

        resultOutput.classList.remove("is-empty");
        resultOutput.innerHTML = `
            <div class="result-status">
                <span>SUCCESS</span>
                <span>${escapeHtml(connectionState.environmentId)}</span>
            </div>
            ${buildResult(activeCommand, inputValue)}
        `;
    });

    window.addEventListener("resize", () => {
        if (connectionState.connectionStatus === "connected") {
            snapCommand();
            return;
        }

        setCommandPosition(commandPosition.x, commandPosition.y);
    });

    renderCommand(commandsById.get(commandBlocks[0]?.dataset.command));
    updateEnvironment(activeEnvironment);
}
