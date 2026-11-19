package com.alabtnt.bingo.config;

/** Client-side UI and integration settings, stored in config/bingo-ourselves/client.json. */
public final class ClientConfig {
	public int configVersion = 1;

	// Board HUD
	public boolean hudEnabled = true;
	public String hudAnchor = "top_left";
	public int hudOffsetX = 4;
	public int hudOffsetY = 4;
	public float hudScale = 1.0f;
	public float hudOpacity = 0.85f;
	public boolean hudBackground = true;
	public boolean showNames = false;
	public boolean showTimer = true;
	public boolean hideOnDebug = true;
	public boolean hideOnChat = false;
	public int hudColor = 0xFFFFFF;

	// Self-built minimap
	public int minimapSize = 96;
	public String minimapAnchor = "top_right";
	public int minimapOffsetX = 4;
	public int minimapOffsetY = 4;
	public float minimapOpacity = 0.85f;
	public boolean minimapNorthUp = true;

	// Board screen
	public float screenScale = 1.0f;
	public boolean showTeamProgress = true;

	// Integrations (client-side toggles)
	public boolean enableJei = true;
	public boolean enableMinimap = true;
	public boolean enableWorldMap = true;
	public boolean enableInventorySorting = true;
	public boolean enableReplay = true;

	public Anchor anchor() {
		try {
			return Anchor.valueOf(hudAnchor.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return Anchor.TOP_LEFT;
		}
	}

	public Anchor minimapAnchor() {
		try {
			return Anchor.valueOf(minimapAnchor.toUpperCase(java.util.Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return Anchor.TOP_RIGHT;
		}
	}

	public enum Anchor {
		TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
	}
}
