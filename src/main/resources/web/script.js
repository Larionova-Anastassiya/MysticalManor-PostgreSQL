
const getElement = (id) => document.getElementById(id);

let currentPlayerId = null;

// API Requests

async function api(path, options = {}) {
    const response = await fetch(path, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            ...options.headers
        }
    });

    const data = await response.json();

    if (!response.ok) {
        throw new Error(data.error || "Request failed");
    }

    return data;
}

// Render Player Data

function renderPlayer(player) {
    currentPlayerId = player.id;

    localStorage.setItem(
        "manorPlayerId",
        String(player.id)
    );

    // Switch screens
    getElement("welcome").hidden = true;
    getElement("game").hidden = false;

    // Player statistics
    getElement("identity").textContent =
        `${player.name} · ${player.characterClass}`;

    getElement("health").textContent =
        `♥ ${player.health}/${player.maxHealth}`;

    getElement("gold").textContent =
        `◈ ${player.gold} gold`;

    getElement("xp").textContent =
        `✦ ${player.experience} XP`;

    getElement("id").textContent =
        `Save #${player.id}`;

    // Current room
    getElement("room").textContent = player.room;

    if (player.room === "Gallery") {
        getElement("sceneTitle").textContent = "The Living Gallery";
    } else if (player.room === "Library") {
        getElement("sceneTitle").textContent = "The Whispering Library";
    } else {
        getElement("sceneTitle").textContent = "The Grand Hall";
    }

    // Story message
    getElement("message").textContent = player.message;

    // Inventory
    const inventoryItems = player.inventory.length
        ? player.inventory
        : ["Nothing yet"];

    getElement("inventory").replaceChildren(
        ...inventoryItems.map((item) => {
            const li = document.createElement("li");
            li.textContent = item;
            return li;
        })
    );

    // Discovered paintings
    const discoveredPaintings = player.paintings.length
        ? player.paintings
        : ["No paintings discovered"];

    getElement("paintings").replaceChildren(
        ...discoveredPaintings.map((painting) => {
            const li = document.createElement("li");
            li.textContent = painting;
            return li;
        })
    );

    // Clear errors
    getElement("error").textContent = "";
}

// Error Handling

async function handleRequest(callback) {
    try {
        await callback();
    } catch (error) {
        getElement("error").textContent = error.message;
    }
}

// Create New Character

getElement("create").onclick = () => {
    handleRequest(async () => {
        const player = await api("/api/players", {
            method: "POST",
            body: JSON.stringify({
                name: getElement("name").value,
                characterClass: getElement("class").value
            })
        });

        renderPlayer(player);
    });
};

// Resume Saved Game

getElement("resume").onclick = () => {
    handleRequest(async () => {
        const playerId = getElement("resumeId").value;

        const player = await api(
            `/api/players/${playerId}`
        );

        renderPlayer(player);
    });
};

// Game Actions

document.querySelectorAll("[data-action]").forEach((button) => {
    button.onclick = () => {
        handleRequest(async () => {
            const action = button.dataset.action;

            const player = await api(
                `/api/players/${currentPlayerId}/actions`,
                {
                    method: "POST",
                    body: JSON.stringify({
                        action: action
                    })
                }
            );

            renderPlayer(player);
        });
    };
});

// Switch Character

getElement("switch").onclick = () => {
    getElement("game").hidden = true;
    getElement("welcome").hidden = false;
};

// Automatically Load Last Saved Player

const savedPlayerId = localStorage.getItem("manorPlayerId");

if (savedPlayerId) {
    getElement("resumeId").value = savedPlayerId;

    handleRequest(async () => {
        const player = await api(
            `/api/players/${savedPlayerId}`
        );

        renderPlayer(player);
    });
}
