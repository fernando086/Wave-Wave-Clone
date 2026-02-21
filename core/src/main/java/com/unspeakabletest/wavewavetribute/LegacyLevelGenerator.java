package com.unspeakabletest.wavewavetribute;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.unspeakabletest.wavewavetribute.enums.Difficulty;

public class LegacyLevelGenerator implements LevelGenerator {

    // Geometry Constants (Defined first for usage below)
    private static final float WAVE_VERTICAL_SPEED = 400f;
    private static final float WAVE_HORIZONTAL_SPEED = 300f;
    private static final float GRID_SLOPE = WAVE_VERTICAL_SPEED / WAVE_HORIZONTAL_SPEED; // 1.33333...

    private static final float VISIBLE_HEIGHT = 300f; // Height of the colored band
    private static final float CENTER_Y = 240f; // Center of the screen (480 / 2)
    private static final float MIN_Y = CENTER_Y - (VISIBLE_HEIGHT / 2);
    private static final float MAX_Y = CENTER_Y + (VISIBLE_HEIGHT / 2);

    // Grid Constants
    private static final int GRID_ROWS = 9;
    private static final float TRIANGLE_HEIGHT = VISIBLE_HEIGHT / GRID_ROWS; // 33.333f
    private static final float TRIANGLE_WIDTH = (TRIANGLE_HEIGHT / GRID_SLOPE) * 2f; // Matched to Wave Slope

    private FastNoise noiseGenerator;
    private float noiseScale = 0.1f; // Adjust for clustering size

    private Color mainColor;
    private Color secondaryColor;
    private Color darkColor;

    public Color getObstacleColor() {
        if (isWaveMode)
            return Color.WHITE;
        return darkColor;
    }

    // Obstacle management
    private static class TriangleObstacle {
        // Logical shape (Player Collision)
        float x1, y1, x2, y2, x3, y3;

        // Render shape (Epsilon Expanded for Vertex Containment)
        float rx1, ry1, rx2, ry2, rx3, ry3;

        boolean isCeiling;
        boolean active;

        public void set(float tipX, float tipY, float farBaseY, float slope, boolean isCeiling) {
            this.isCeiling = isCeiling;
            this.active = true;

            // 1. Calculate Logical Shape (Strict)
            float heightDiff = Math.abs(farBaseY - tipY);
            float halfWidth = heightDiff / slope;

            this.x1 = tipX - halfWidth;
            this.y1 = farBaseY;
            this.x2 = tipX + halfWidth;
            this.y2 = farBaseY;
            this.x3 = tipX;
            this.y3 = tipY;

            // 2. Calculate Render Shape (Expanded Buffer)
            // Buffer ensures "Critical Vertices" on the boundary are captured even with
            // float error
            float buffer = 25f;
            float rHalfWidth = (heightDiff / slope) + buffer;

            this.rx1 = tipX - rHalfWidth;
            this.ry1 = farBaseY;
            this.rx2 = tipX + rHalfWidth;
            this.ry2 = farBaseY;
            this.rx3 = tipX;
            this.ry3 = tipY; // Tip Y stays strict? No, generally expansion is lateral for diagonal coverage
                             // Tip Y is critical for vertical quantization. Keep Tip Y strict.
        }
    }

    private final com.badlogic.gdx.utils.Array<TriangleObstacle> obstacles = new com.badlogic.gdx.utils.Array<>();
    private float lastObstacleX = 200; // Start obstacles a bit ahead
    private float obstacleSpacing = 200f; // Distance between obstacle columns (increased from 100)

    public LegacyLevelGenerator(Difficulty difficulty) {
        setColorsForDifficulty(difficulty);
        noiseGenerator = new FastNoise(com.badlogic.gdx.math.MathUtils.random(1000));
        nextIsTop = com.badlogic.gdx.math.MathUtils.randomBoolean(); // Randomize start direction
    }

    private void setColorsForDifficulty(Difficulty difficulty) {
        // Palette Init
        // Refined based on User Screenshot (Crystalline Look)
        // Bottom: Indigo/Deep Blue
        // Mid-Low: Royal Blue
        // Mid-High: Teal/Turquoise (The distinct green-blue note)
        // Top: Icy White

        switch (difficulty) {
            case WAVE:
            default:
                palette = new Color[] {
                        Color.valueOf("2B3A67"), // Deep Indigo (Bottom)
                        Color.valueOf("496A9E"), // Muted Blue
                        Color.valueOf("6699CC"), // Lighter Blue
                        Color.valueOf("66CCAA"), // Teal/Turquoise (Key for the look)
                        Color.valueOf("DDF9F9") // Icy White (Top)
                };
                darkColor = Color.valueOf("1a2e54"); // Obstacle Color
                break;
            // Add others later
        }

        mainColor = palette[0]; // Fallback
    }

    private boolean nextIsTop = true; // Start pattern with top or bottom
    private float lastObstacleEndX = 200; // Track end of last obstacle

    // Assembly FX
    public boolean ENABLE_ASSEMBLY_FX = true;
    private float gameSpeed = 300f; // Default, should be updated from GameScreen
    // Assembly Parameters
    private final float ASSEMBLY_WIDTH_OFFSET = 300f; // Distance from Right Edge where assembly "starts" (0 offset).
                                                      // Increased to show more cols.
    private final float ASSEMBLY_ZONE_WIDTH = 600f; // Width of the assembly zone. Smoother gradient.
    private final float ASSEMBLY_MAX_OFFSET_Y = 600f; // Max vertical spawn offset
    private final float ASSEMBLY_MAX_OFFSET_X = 400f; // Max horizontal spawn offset (coming from right)

    // Color Palette
    private Color[] palette;
    private final Color tempColor = new Color();

    public void setGameSpeed(float speed) {
        this.gameSpeed = speed;
    }

    // Wobble Effect (Geometric Shear)
    private float currentWobbleAngle = 0;
    private float shearAngle = 0; // Smoothed angle for Jelly lag

    public void setWobbleAngle(float angle) {
        this.currentWobbleAngle = angle;
    }

    // --- GLITCH EFFECT (Paper Turn) ---
    public boolean ENABLE_GLITCH_FX = false; // Enabled after Hit Stop
    private boolean isWaveMode = false;

    public void setWaveMode(boolean active) {
        this.isWaveMode = active;
    }

    private final com.badlogic.gdx.utils.Array<GlitchInstance> activeGlitches = new com.badlogic.gdx.utils.Array<>();
    private float glitchSpawnTimer = 0;

    private static class GlitchInstance {
        int col;
        int row;
        float lifeTime;
        float duration;
        boolean isUp; // We need to know orientation to match grid logic

        public GlitchInstance(int col, int row, boolean isUp) {
            this.col = col;
            this.row = row;
            this.isUp = isUp;
            this.lifeTime = 0;
            this.duration = 0.4f; // 0.2s to close, 0.2s to open
        }
    }

    public void updateGlitches(float delta, int startCol, int endCol) {
        if (!ENABLE_GLITCH_FX)
            return;

        // 1. Update active glitches
        for (int i = activeGlitches.size - 1; i >= 0; i--) {
            GlitchInstance g = activeGlitches.get(i);
            g.lifeTime += delta;
            if (g.lifeTime >= g.duration) {
                activeGlitches.removeIndex(i);
            }
        }

        // 2. Spawn new glitches randomly
        glitchSpawnTimer += delta;
        if (glitchSpawnTimer > 0.05f) { // Spawn check freq
            glitchSpawnTimer = 0;
            if (com.badlogic.gdx.math.MathUtils.randomBoolean(0.3f)) { // 30% chance per check
                // Pick random visible triangle
                int rCol = com.badlogic.gdx.math.MathUtils.random(startCol, endCol);
                int rRow = com.badlogic.gdx.math.MathUtils.random(0, GRID_ROWS - 1); // Include all rows
                boolean rIsUp = com.badlogic.gdx.math.MathUtils.randomBoolean();

                // Check if already glitching
                boolean exists = false;
                for (GlitchInstance g : activeGlitches) {
                    if (g.col == rCol && g.row == rRow && g.isUp == rIsUp) {
                        exists = true;
                        break;
                    }
                }

                if (!exists) {
                    activeGlitches.add(new GlitchInstance(rCol, rRow, rIsUp));
                }
            }
        }
    }

    private float getGlitchScale(int col, int row, boolean isUp) {
        for (GlitchInstance g : activeGlitches) {
            if (g.col == col && g.row == row && g.isUp == isUp) {
                // Life: 0 -> 0.4
                // Phase 1 (0 -> 0.2): Scale 1 -> 0
                // Phase 2 (0.2 -> 0.4): Scale 0 -> 1
                float halfLife = g.duration / 2f;
                if (g.lifeTime < halfLife) {
                    return 1f - (g.lifeTime / halfLife);
                } else {
                    return (g.lifeTime - halfLife) / halfLife;
                }
            }
        }
        return 1f; // Normal
    }

    /**
     * Instantly resets all wobble and shear effects to zero.
     * Used for Game Over "Snap" effect.
     */
    public void resetWobble() {
        this.currentWobbleAngle = 0;
        this.shearAngle = 0;
        this.latchedShearTarget = 0;
    }

    public float getShearAngle() {
        return shearAngle;
    }

    private float lastCameraY = 0;

    // Interface method
    @Override
    public void update(float delta, float cameraX) {
        update(delta, cameraX, 0); // Warning: No Y info
    }

    private float latchedShearTarget = 0; // Stick to +/- 3.0 until opposite side triggers

    public void update(float delta, float cameraX, float cameraY) {
        this.lastCameraY = cameraY;

        // Update Smoothed Shear Angle (Jelly Effect)
        // Logic:
        // 1. If Wobble > 0.5 -> Target = +3.0
        // 2. If Wobble < -0.5 -> Target = -3.0
        // 3. If in between -> Keep existing Target (Latch)

        float threshold = 0.5f; // Reduced from 1.5f as requested

        if (currentWobbleAngle > threshold) {
            latchedShearTarget = 3.0f;
        } else if (currentWobbleAngle < -threshold) {
            latchedShearTarget = -3.0f;
        }

        // "Sinusoidal" Transition (Smooth Ease-In/Out feel)
        // Ideally we'd use a Sine function, but Lerp at moderate speed approximates the
        // "Soft Catchup".
        float lerpSpeed = 0.75f * delta;
        shearAngle += (latchedShearTarget - shearAngle) * lerpSpeed;

        // Simple Speed Estimation (if not passed explicitly)
        // Or we can just use a fixed value for the effect if real speed isn't critical.
        // User said "Depend on speed".
        // Let's assume constant speed for now or add a setter.
        // Better: Calculate from camera movement?
        // float currentSpeed = (cameraX - lastCameraX) / delta;
        // For now, let's stick to the default 300f or update it if we can.

        // Generate obstacles ahead of the camera
        while (lastObstacleEndX < cameraX + 800) {
            generateObstacleColumn();
        }
    }

    private void generateObstacleColumn() {
        // Strict alternating pattern
        // Consistent slope matching Wave Movement (and now Grid)
        int maxRows = GRID_ROWS - 1; // 8

        // Ensure visibility inside the "Colored Region" (e.g., Rows 2-6)
        // Ceiling Obstacle: Starts at Row 8 (Top) going down. To reach visible region
        // (Row 6), it needs height >= 3 (8,7,6).
        // Floor Obstacle: Starts at Row 0 (Bottom) going up. To reach visible region
        // (Row 2), it needs height >= 3 (0,1,2).

        // New Constraint: Min Height 3 to ensure it pokes into the center.
        // --- Anti-AFK Logic ---
        // First 3 obstacles must have at least 6 rows to blocking center path deeply
        int minRows = 2;
        if (obstaclesGenerated < 3) {
            minRows = 6;
        }

        // Safety: ensure min <= max
        if (minRows > maxRows)
            minRows = maxRows;

        int heightRows = com.badlogic.gdx.math.MathUtils.random(minRows, maxRows);

        float visualHeight = heightRows * TRIANGLE_HEIGHT;
        float slope = GRID_SLOPE;

        // Calculate the "Visual Width" at the base
        // Slope = dy/dx. dx = dy/Slope.
        // HalfWidth = VisualHeight / Slope.
        // Full Width = 2 * HalfWidth
        float visualBaseWidth = (visualHeight / slope) * 2f;

        // Reduced Gap for higher density: 20f to 50f
        float gap = com.badlogic.gdx.math.MathUtils.random(20f, 50f);

        float startX = lastObstacleEndX + gap;
        float snapStep = TRIANGLE_WIDTH / 2f;

        float proposedCenterX = startX + visualBaseWidth / 2;
        int colIndex = Math.round(proposedCenterX / snapStep);

        // --- PARITY SNAPPING FOR PERFECT TIPS ---
        if (nextIsTop) {
            // Ceiling Mountain (Points Down)
            // Tip Y = MAX_Y - visualHeight
            // MAX_Y = Top of Row 8 (if 9 rows 0..8). = MIN_Y + 9*H.
            // Tip Y = MIN_Y + (9 - heightRows)*H.
            // This Y corresponds to the Bottom of Row (9 - heightRows).
            // A Down-Triangle in Row R has its tip at Bottom of Row R.
            // So we need a DOWN triangle in Row R = (9 - heightRows).
            // Is this row visible? If heightRows=2, R=7. Row 7 is visible. OK.

            int tipRowIndex = GRID_ROWS - heightRows;

            // In Row 'tipRowIndex', which columns are DOWN?
            // Row Even: Odd Cols are DOWN.
            // Row Odd: Even Cols are DOWN.
            boolean rowIsEven = (tipRowIndex % 2 == 0);
            boolean colMustBeEven = !rowIsEven; // If Row Odd -> Even Cols. If Row Even -> Odd Cols.

            // Check current colIndex parity
            if ((colIndex % 2 == 0) != colMustBeEven) {
                colIndex++; // Shift to matching column
            }

        } else {
            // Floor Mountain (Points Up)
            // Tip Y = MIN_Y + visualHeight
            // Tip Y = MIN_Y + heightRows*H.
            // This corresponds to Top of Row (heightRows - 1).
            // An UP-Triangle in Row R has its tip at Top of Row R.
            // So we need an UP triangle in Row R = (heightRows - 1).

            int tipRowIndex = heightRows - 1;

            // In Row 'tipRowIndex', which columns are UP?
            // Row Even: Even Cols are UP.
            // Row Odd: Odd Cols are UP.
            boolean rowIsEven = (tipRowIndex % 2 == 0);
            boolean colMustBeEven = rowIsEven; // Even->Even, Odd->Odd

            // Check parity
            if ((colIndex % 2 == 0) != colMustBeEven) {
                colIndex++;
            }
        }

        float centerX = colIndex * snapStep;
        float endX = centerX + visualBaseWidth / 2;

        TriangleObstacle obs = new TriangleObstacle();
        float extendedY = 1000f;

        if (nextIsTop) {
            float tipY = MAX_Y - visualHeight;
            float tipX = centerX;
            float farBaseY = MAX_Y + extendedY;
            // Calculations moved inside set()
            obs.set(tipX, tipY, farBaseY, slope, true); // isCeiling = true
        } else {
            float tipY = MIN_Y + visualHeight;
            float tipX = centerX;
            float farBaseY = MIN_Y - extendedY;
            obs.set(tipX, tipY, farBaseY, slope, false); // isCeiling = false
        }

        obstacles.add(obs);
        lastObstacleEndX = endX;
        nextIsTop = !nextIsTop;
        obstaclesGenerated++;
    }

    // --- Anti-AFK Counter ---
    private int obstaclesGenerated = 0;

    @Override
    public void renderLevelBase(ShapeRenderer shapeRenderer, OrthographicCamera camera) {
        // Grid Rendering

        // Calculate visible column range
        float camLeft = camera.position.x - (camera.viewportWidth * camera.zoom) / 2 - TRIANGLE_WIDTH;
        float camRight = camera.position.x + (camera.viewportWidth * camera.zoom) / 2 + TRIANGLE_WIDTH;
        // Render Logic

        // 1. Clear Screen to White (User Request: "Unassembled part should be white")
        // This acts as the background for the "void" where triangles haven't assembled
        // yet.
        if (isWaveMode) {
            com.badlogic.gdx.Gdx.gl.glClearColor(0, 0, 0, 1); // Black in Wave Mode
        } else {
            com.badlogic.gdx.Gdx.gl.glClearColor(1, 1, 1, 1);
        }
        com.badlogic.gdx.Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);

        // shapeRenderer.setProjectionMatrix(camera.combined); // Handled by GameScreen
        // shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        // // Handled by GameScreen

        // Grid Logic
        // Rows: 0 to 8 (9 rows)
        // Row 0 is at bottom (MIN_Y)

        int startCol = (int) Math.floor(camLeft / (TRIANGLE_WIDTH / 2)); // Half-width steps
        int endCol = (int) Math.ceil(camRight / (TRIANGLE_WIDTH / 2));

        // PASS 0: Visible Range (White Borders) - NOW BEHIND LAYERS
        // User Request: "White background of top/bottom should be BEHIND the assembly
        // effect"
        // renderVisibleRange(shapeRenderer, camLeft, camRight); // Removed as per
        // request

        // PASS 1: Filled Triangles (Colors) - NOW ON TOP
        renderGridPass(shapeRenderer, startCol, endCol, true,
                camera.position.x + (camera.viewportWidth * camera.zoom) / 2);

        // shapeRenderer.end(); // Handled by GameScreen
    }

    private void renderGridPass(ShapeRenderer shapeRenderer, int startCol, int endCol, boolean isFilled,
            float viewportRightX) {

        // Update Glitches Frame
        updateGlitches(com.badlogic.gdx.Gdx.graphics.getDeltaTime(), startCol, endCol);

        for (int col = startCol; col <= endCol; col++) {
            // Calculate base X of this specific triangle
            float xBase = col * (TRIANGLE_WIDTH / 2);
            float finalX = xBase;

            for (int row = 0; row < GRID_ROWS; row++) {
                boolean isUp;
                if (row % 2 == 0) {
                    isUp = (col % 2 == 0);
                } else {
                    isUp = (col % 2 != 0);
                }

                // Calculate Pair Center for Assembly Drift
                float pairCenterX;
                if (isUp) {
                    pairCenterX = finalX + (TRIANGLE_WIDTH / 2);
                } else {
                    pairCenterX = finalX;
                }

                // Apply Drift Logic
                float baseDriftX = 0;
                float distFromSolid = 0;

                if (ENABLE_ASSEMBLY_FX) {
                    float rowSkew = (row - 4) * (TRIANGLE_WIDTH / 2f);
                    float solidEdgeX = (viewportRightX - ASSEMBLY_WIDTH_OFFSET) + rowSkew;
                    distFromSolid = pairCenterX - solidEdgeX;

                    if (distFromSolid > 0) {
                        float factor = distFromSolid / ASSEMBLY_ZONE_WIDTH;
                        if (factor < 0)
                            factor = 0;
                        if (factor > 1)
                            factor = 1;

                        float curveFactor = (float) Math.pow(factor, 1.5);
                        float speedFactor = gameSpeed / 300f;
                        baseDriftX = curveFactor * ASSEMBLY_MAX_OFFSET_X * 1.5f * speedFactor;
                        if (baseDriftX > ASSEMBLY_MAX_OFFSET_X)
                            baseDriftX = ASSEMBLY_MAX_OFFSET_X;
                    }
                }

                float driftX = baseDriftX;
                float driftY = 0;

                if (ENABLE_ASSEMBLY_FX && baseDriftX > 0) {
                    int centerRow = 4;
                    int rowDiff = row - centerRow;
                    float speedFactor = gameSpeed / 300f;
                    float factor = distFromSolid / ASSEMBLY_ZONE_WIDTH;
                    if (factor < 0)
                        factor = 0;
                    if (factor > 1)
                        factor = 1;
                    float curveFactor = (float) Math.pow(factor, 1.5);
                    float fanStrength = 0.25f;
                    float curvedDist = ASSEMBLY_ZONE_WIDTH * curveFactor;
                    driftY = curvedDist * rowDiff * fanStrength * speedFactor;
                    if (driftY > ASSEMBLY_MAX_OFFSET_Y)
                        driftY = ASSEMBLY_MAX_OFFSET_Y;
                    if (driftY < -ASSEMBLY_MAX_OFFSET_Y)
                        driftY = -ASSEMBLY_MAX_OFFSET_Y;
                    float staggerStrength = 0.2f;
                    driftX += curvedDist * Math.abs(rowDiff) * staggerStrength * speedFactor;
                }

                float rowY = MIN_Y + (row * TRIANGLE_HEIGHT);
                float x1, y1, x2, y2, x3, y3;
                float lx1, ly1, lx2, ly2, lx3, ly3;

                // 1. Initial Geometry
                if (isUp) {
                    x1 = finalX;
                    y1 = rowY;
                    x2 = finalX + TRIANGLE_WIDTH;
                    y2 = rowY;
                    x3 = finalX + TRIANGLE_WIDTH / 2;
                    y3 = rowY + TRIANGLE_HEIGHT;
                } else {
                    x1 = finalX;
                    y1 = rowY + TRIANGLE_HEIGHT;
                    x2 = finalX + TRIANGLE_WIDTH;
                    y2 = rowY + TRIANGLE_HEIGHT;
                    x3 = finalX + TRIANGLE_WIDTH / 2;
                    y3 = rowY;
                }

                lx1 = x1;
                ly1 = y1;
                lx2 = x2;
                ly2 = y2;
                lx3 = x3;
                ly3 = y3;

                // 2. Add Assembly Drift FIRST
                x1 += driftX;
                y1 += driftY;
                x2 += driftX;
                y2 += driftY;
                x3 += driftX;
                y3 += driftY;

                // --- GLITCH SCALING (Paper Turn) ---
                if (ENABLE_GLITCH_FX && activeGlitches.size > 0 && driftX <= 0) { // Only glitch assembled parts
                    float scale = getGlitchScale(col, row, isUp);
                    if (scale < 1f) {
                        // Apply Scale X relative to Center X (x3)
                        float cx = x3;
                        x1 = cx + (x1 - cx) * scale;
                        x2 = cx + (x2 - cx) * scale;
                        // x3 stays same
                    }
                }

                // 3. Apply Global Shear
                float shearK = 0.055f * shearAngle;
                x1 += (y1 - lastCameraY) * shearK;
                x2 += (y2 - lastCameraY) * shearK;
                x3 += (y3 - lastCameraY) * shearK;

                // 4. Collision Check (Synced with LOGIC not Visuals)
                boolean isObstacle = isTriangleBlocked(col, row, lx1, ly1, lx2, ly2, lx3, ly3, isUp,
                        Float.NEGATIVE_INFINITY) != null;

                if (isFilled) {
                    Color c;
                    if (isObstacle) {
                        c = getObstacleColorForTriangle(col, row);
                        if (isWaveMode)
                            c = Color.WHITE;
                    } else if (driftX > 0 && !isWaveMode) { // Only do fancy assembly if NOT in Wave Mode (or handle it)
                        // Restore Assembly Logic for Normal Mode
                        float progress = 1f - (driftX / ASSEMBLY_ZONE_WIDTH);
                        if (progress < 0)
                            progress = 0;
                        if (progress > 1)
                            progress = 1;
                        Color targetColor = getMainColorForTriangle(col, row, finalX + TRIANGLE_WIDTH / 2,
                                rowY + TRIANGLE_HEIGHT / 2);
                        c = new Color(Color.WHITE).lerp(targetColor, progress);
                    } else {
                        // Main Grid or Wave Mode Grid
                        if (isWaveMode) {
                            c = Color.BLACK;
                        } else {
                            c = getMainColorForTriangle(col, row, finalX + TRIANGLE_WIDTH / 2,
                                    rowY + TRIANGLE_HEIGHT / 2);
                        }
                    }
                    shapeRenderer.setColor(c);
                    shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
                }
            }
        }
    }

    private TriangleObstacle isTriangleBlocked(int col, int row, float x1, float y1, float x2, float y2, float x3,
            float y3, boolean isUp, float playerX) {
        // Determine Centroid and Critical Points
        float cx = (x1 + x2 + x3) / 3f;

        float highX, highY;
        float lowX, lowY;

        if (isUp) {
            highX = x3;
            highY = y3;
            lowX = (x1 + x2) / 2f;
            lowY = y1;
        } else {
            highX = (x1 + x2) / 2f;
            highY = y1;
            lowX = x3;
            lowY = y3;
        }

        for (TriangleObstacle obs : obstacles) {
            // Optimization
            // 1. Fair Collision: If Player has passed the Tip (x3), ignore this obstacle.
            if (playerX > obs.x3)
                continue;

            if (obs.rx2 < cx - 500)
                continue;
            if (obs.rx1 > cx + 500)
                continue;

            if (obs.isCeiling) {
                if (!isUp) {
                    // Good Edge (Down Tri)
                    if (com.badlogic.gdx.math.Intersector.isPointInTriangle(lowX, lowY, obs.rx1, obs.ry1, obs.rx2,
                            obs.ry2, obs.rx3, obs.ry3)) {
                        return obs;
                    }
                } else {
                    // Bad Edge (Up Tri) - Strict Check
                    boolean allIn = true;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x1, y1, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x2, y2, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x3, y3, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (allIn)
                        return obs;
                }
            } else {
                if (isUp) {
                    // Good Edge (Up Tri)
                    if (com.badlogic.gdx.math.Intersector.isPointInTriangle(highX, highY, obs.rx1, obs.ry1, obs.rx2,
                            obs.ry2, obs.rx3, obs.ry3)) {
                        return obs;
                    }
                } else {
                    // Bad Edge (Down Tri) - Strict Check
                    boolean allIn = true;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x1, y1, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x2, y2, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x3, y3, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                            obs.rx3, obs.ry3))
                        allIn = false;
                    if (allIn)
                        return obs;
                }
            }
        }
        return null;
    }

    private Color getMainColorForTriangle(int col, int row, float worldX, float worldY) {
        // Algorithm: Distorted Gradient
        // 1. Start with exact Row Ratio (0.0 Bottom to 1.0 Top)
        float rowRatio = (float) row / (float) (GRID_ROWS - 1);

        // 2. Large Scale Distortion (The "Waves" of color)
        // Shifts the gradient up/down significantly.
        float waveNoise = noiseGenerator.GetNoise(col * 0.12f, row * 0.1f);
        float waveDistortion = waveNoise * 0.35f; // Strong shift (approx +/- 1.5 palette indices)

        // 3. Medium Scale "Crystals" (The geometric texture)
        // Adds local variation to break smooth bands.
        float crystalNoise = noiseGenerator.GetNoise(col * 0.45f, row * 0.45f);
        float crystalDistortion = crystalNoise * 0.15f;

        // 4. Combine
        // We distort the "input coordinate" (rowRatio) rather than just adding noise to
        // color.
        float effectiveRatio = rowRatio + waveDistortion + crystalDistortion;

        // Clamp 0..1
        effectiveRatio = com.badlogic.gdx.math.MathUtils.clamp(effectiveRatio, 0f, 1f);

        // Map to Palette
        float maxIndex = palette.length - 1;
        float scaledValue = effectiveRatio * maxIndex;

        int indexA = (int) scaledValue;
        int indexB = indexA + 1;

        // Clamp Indices
        if (indexA > maxIndex)
            indexA = (int) maxIndex;
        if (indexB > maxIndex)
            indexB = (int) maxIndex;
        if (indexA < 0)
            indexA = 0;

        float t = scaledValue - indexA;

        tempColor.set(palette[indexA]);
        tempColor.lerp(palette[indexB], t);

        return tempColor;
    }

    private Color getObstacleColorForTriangle(int col, int row) {
        // Just Dark color with slight noise for texture
        float noiseVal = noiseGenerator.GetNoise(col * 0.2f, row * 0.2f);
        Color c = new Color(darkColor);
        if (noiseVal > 0.5f) {
            c.lerp(Color.BLACK, 0.2f);
        }
        return c;
    }

    @Override
    public void renderLevelMask(ShapeRenderer shapeRenderer, OrthographicCamera camera) {
        // Mask Removed: The Grid loop only draws Rows 0-8.
        // The background is cleared to White.
        // Drawing explicit White Overlays caused z-fighting with the Sheared Grid at
        // the edges.
    }

    @Override
    public void renderObstacles(ShapeRenderer shapeRenderer, OrthographicCamera camera) {
        // Obstacles are now rendered as part of the Grid (renderLevelBase)
        // This method remains empty to prevent double rendering, or we can use it for
        // debug lines later.
    }

    private TriangleObstacle lastCollidedObstacle = null;
    private boolean lastCollisionWasOuter = false;
    private boolean lastCollisionWasCeiling = false;

    public boolean wasLastCollisionOuter() {
        return lastCollisionWasOuter;
    }

    public boolean wasLastCollisionCeiling() {
        return lastCollisionWasCeiling;
    }

    public void renderCollidedObstacle(ShapeRenderer shapeRenderer) {
        if (lastCollidedObstacle != null) {
            shapeRenderer.setColor(darkColor);

            // Calculate Grid Bounds for this Obstacle
            TriangleObstacle obs = lastCollidedObstacle;

            // X Range
            // We need to cover from obs.x1 to obs.x2
            int startCol = (int) Math.floor(obs.x1 / (TRIANGLE_WIDTH / 2)) - 2;
            int endCol = (int) Math.ceil(obs.x2 / (TRIANGLE_WIDTH / 2)) + 2;

            // Y Range
            // Base to Tip
            float minY_Obs = Math.min(obs.y1, obs.y3);
            float maxY_Obs = Math.max(obs.y1, obs.y3);

            int startRow = (int) Math.floor((minY_Obs - MIN_Y) / TRIANGLE_HEIGHT) - 1;
            int endRow = (int) Math.ceil((maxY_Obs - MIN_Y) / TRIANGLE_HEIGHT) + 1;

            // Iterate and Render "Blocked" Triangles
            for (int col = startCol; col <= endCol; col++) {
                for (int row = startRow; row <= endRow; row++) {
                    // Geometry
                    float xBase = col * (TRIANGLE_WIDTH / 2);
                    float finalX = xBase;
                    boolean isUp = (row % 2 == 0) ? (col % 2 == 0) : (col % 2 != 0);

                    float x1, y1, x2, y2, x3, y3;
                    float rowY = MIN_Y + (row * TRIANGLE_HEIGHT);

                    if (isUp) {
                        x1 = finalX;
                        y1 = rowY;
                        x2 = finalX + TRIANGLE_WIDTH;
                        y2 = rowY;
                        x3 = finalX + TRIANGLE_WIDTH / 2;
                        y3 = rowY + TRIANGLE_HEIGHT;
                    } else {
                        x1 = finalX;
                        y1 = rowY + TRIANGLE_HEIGHT;
                        x2 = finalX + TRIANGLE_WIDTH;
                        y2 = rowY + TRIANGLE_HEIGHT;
                        x3 = finalX + TRIANGLE_WIDTH / 2;
                        y3 = rowY;
                    }

                    // Apply Global Shear (Wobble) to Collision Feedback
                    float shearK = 0.055f * shearAngle;

                    x1 += (y1 - lastCameraY) * shearK;
                    x2 += (y2 - lastCameraY) * shearK;
                    x3 += (y3 - lastCameraY) * shearK;

                    // Check if this specific triangle belongs to the obstacle
                    // Note: isTriangleBlocked currently iterates ALL obstacles.
                    // We want to know if it's blocked by *this* specific obstacle.
                    // Let's use a helper that checks specific obstacle logic.
                    if (isBlockedBySpecificObstacle(obs, col, row, x1, y1, x2, y2, x3, y3, isUp)) {
                        shapeRenderer.triangle(x1, y1, x2, y2, x3, y3);
                    }
                }
            }
        }
    }

    private boolean isBlockedBySpecificObstacle(TriangleObstacle obs, int col, int row, float x1, float y1, float x2,
            float y2, float x3, float y3, boolean isUp) {

        float highX, highY;
        float lowX, lowY;

        if (isUp) {
            highX = x3;
            highY = y3;
            lowX = (x1 + x2) / 2f;
            lowY = y1;
        } else {
            highX = (x1 + x2) / 2f;
            highY = y1;
            lowX = x3;
            lowY = y3;
        }

        if (obs.isCeiling) {
            if (!isUp) {
                // Good Edge (Down Tri)
                if (com.badlogic.gdx.math.Intersector.isPointInTriangle(lowX, lowY, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3)) {
                    return true;
                }
            } else {
                // Bad Edge (Up Tri)
                boolean allIn = true;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x1, y1, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x2, y2, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x3, y3, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (allIn)
                    return true;
            }
        } else {
            if (isUp) {
                // Good Edge (Up Tri)
                if (com.badlogic.gdx.math.Intersector.isPointInTriangle(highX, highY, obs.rx1, obs.ry1, obs.rx2,
                        obs.ry2, obs.rx3, obs.ry3)) {
                    return true;
                }
            } else {
                // Bad Edge (Down Tri)
                boolean allIn = true;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x1, y1, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x2, y2, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (!com.badlogic.gdx.math.Intersector.isPointInTriangle(x3, y3, obs.rx1, obs.ry1, obs.rx2, obs.ry2,
                        obs.rx3, obs.ry3))
                    allIn = false;
                if (allIn)
                    return true;
            }
        }
        return false;
    }

    @Override
    public boolean checkCollision(com.badlogic.gdx.math.Vector2 playerPosition) {
        float px = playerPosition.x;
        float py = playerPosition.y;

        // "Unshear" the player position to check against static grid logic
        // Shear logic: x_vis = x + (y - lastCameraY) * shearK
        // So x = x_vis - (y - lastCameraY) * shearK

        float shearK = 0.055f * shearAngle;
        float effPx = px - (py - lastCameraY) * shearK;

        // Use effective Px for column lookup
        // We still use real Py because shear is horizontal only.

        // 1. Calculate Grid Coordinates
        // Row index:
        int row = (int) Math.floor((py - MIN_Y) / TRIANGLE_HEIGHT); // Use Real Py (Row implies Y)

        // Base Col index approx:
        int baseCol = (int) Math.floor(effPx / (TRIANGLE_WIDTH / 2)); // Use Effective Px

        // Check 3 columns around baseCol to be safe (col-1, col, col+1)

        // Check 3 columns around baseCol to be safe (col-1, col, col+1)
        for (int col = baseCol - 1; col <= baseCol + 1; col++) {
            // 2. Reconstruct Triangle Geometry for this (col, row)
            float xBase = col * (TRIANGLE_WIDTH / 2);
            float finalX = xBase;
            boolean isUp = (row % 2 == 0) ? (col % 2 == 0) : (col % 2 != 0);

            float x1, y1, x2, y2, x3, y3;
            float rowY = MIN_Y + (row * TRIANGLE_HEIGHT);

            if (isUp) {
                x1 = finalX;
                y1 = rowY;
                x2 = finalX + TRIANGLE_WIDTH;
                y2 = rowY;
                x3 = finalX + TRIANGLE_WIDTH / 2;
                y3 = rowY + TRIANGLE_HEIGHT;
            } else {
                x1 = finalX;
                y1 = rowY + TRIANGLE_HEIGHT;
                x2 = finalX + TRIANGLE_WIDTH;
                y2 = rowY + TRIANGLE_HEIGHT;
                x3 = finalX + TRIANGLE_WIDTH / 2;
                y3 = rowY;
            }

            // 3. Check if Player is in THIS triangle
            // Use EFFECTIVE Px (Unsheared) to check against Unsheared Grid Triangle
            if (com.badlogic.gdx.math.Intersector.isPointInTriangle(effPx, py, x1, y1, x2, y2, x3, y3)) {
                // 4. Check if this triangle is an Obstacle
                // Pass effPx (Unsheared Player X) for Fair Collision
                TriangleObstacle blockedBy = isTriangleBlocked(col, row, x1, y1, x2, y2, x3, y3, isUp, effPx);
                if (blockedBy != null) {
                    // Hit!
                    lastCollisionWasOuter = (py > MAX_Y || py < MIN_Y);
                    lastCollisionWasCeiling = (py > CENTER_Y);

                    // Grab the FULL obstacle that we hit
                    lastCollidedObstacle = blockedBy;

                    return true;
                }
            }
        }

        // Also check if out of bounds (Top/Bottom white bars)

        return false;
    }
}
