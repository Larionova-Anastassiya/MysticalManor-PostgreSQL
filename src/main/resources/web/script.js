
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

// The Cursed Painting — server-authoritative puzzle, saved in PostgreSQL.
let activePuzzleId = null;
let puzzleCatalog = [];

async function refreshPuzzleCatalog(player) {
    puzzleCatalog = await api('/api/paintings');
    const select = getElement('puzzlePainting');
    select.replaceChildren(...puzzleCatalog.map(p => {
        const option = document.createElement('option');
        option.value = String(p.id);
        option.textContent = `${p.title} · +${p.rewardGold} gold / +${p.rewardXp} XP`;
        option.disabled = !player.paintings.includes(p.title);
        return option;
    }));
    const unlocked = puzzleCatalog.find(p => player.paintings.includes(p.title));
    if (unlocked) select.value = String(unlocked.id);
    getElement('startPuzzle').disabled = !unlocked;
}

function showPuzzle(attempt) {
    activePuzzleId = attempt.id;
    getElement('puzzleGame').hidden = false;
    getElement('puzzleStatus').textContent =
        `${attempt.paintingTitle} · ${attempt.status} · Turn ${attempt.moves}/${attempt.maxMoves}` +
        (attempt.moves ? ` · Exact: ${attempt.lastExact}, Present: ${attempt.lastPresent}` : '');
    getElement('puzzleHistory').replaceChildren(...attempt.guesses.map(entry => {
        const li = document.createElement('li');
        li.textContent = entry;
        return li;
    }));
    getElement('submitRunes').disabled = attempt.status !== 'ACTIVE';
    if (attempt.status === 'WON') getElement('puzzleStatus').textContent += ' · Seal broken! Rewards saved.';
    if (attempt.status === 'LOST') getElement('puzzleStatus').textContent += ' · The seal remains. Try a new attempt.';
}

async function puzzleRequest(fn) {
    try {
        getElement('puzzleError').textContent = '';
        await fn();
    } catch (error) {
        getElement('puzzleError').textContent = error.message;
    }
}

getElement('startPuzzle').onclick = () => puzzleRequest(async () => {
    if (!currentPlayerId) throw Error('Create or load a character first.');
    const attempt = await api('/api/attempts', {
        method: 'POST',
        body: JSON.stringify({ playerId: currentPlayerId, paintingId: Number(getElement('puzzlePainting').value) })
    });
    showPuzzle(attempt);
});

getElement('submitRunes').onclick = () => puzzleRequest(async () => {
    if (!activePuzzleId) throw Error('Start a puzzle first.');
    const runes = [...document.querySelectorAll('.rune')].map(select => select.value);
    const attempt = await api(`/api/attempts/${activePuzzleId}`, {
        method: 'PATCH', body: JSON.stringify({ runes })
    });
    showPuzzle(attempt);
    if (attempt.status === 'WON') {
        const player = await api(`/api/players/${currentPlayerId}`);
        renderPlayer(player);
        await refreshPuzzleCatalog(player);
    }
});

// Keep the original renderPlayer implementation and extend it without changing game mechanics.
const originalRenderPlayer = renderPlayer;
renderPlayer = function (player) {
    originalRenderPlayer(player);
    puzzleRequest(() => refreshPuzzleCatalog(player));
};
