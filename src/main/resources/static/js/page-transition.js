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
