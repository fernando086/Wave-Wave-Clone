package com.unspeakabletest.wavewavetribute;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ScreenAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;

public class GameScreen extends ScreenAdapter {

    private final MainGame game;
    private OrthographicCamera camera;
    private ShapeRenderer shapeRenderer;
    private LevelGenerator levelGenerator;

    // Player properties
    private Vector2 playerPosition;
    private float verticalSpeed = 400f; // Pixels per second
    private float horizontalSpeed = 300f; // Pixels per second
    private int currentDirection = 1; // 1 for up, -1 for down

    // Trail
    private Array<Vector2> trail;
    private float timeSinceLastTrailPoint = 0;
    private float trailInterval = 0.05f; // Add a point every 0.05 seconds

    private float wobbleTimer = 0;
    private float shakeTimer = 0; // Screen Shake Duration
    private float hitStopTimer = 0; // Freeze Frame Duration
    private Vector2 crashPoint = new Vector2(); // Store impact point for delayed zoom
    private final float MAX_WOBBLE_ANGLE = 3f; // Reduced from 10f as requested
    private boolean isWobbleActive = true;

    // --- START BURST EFFECT (Rocket Smoke) ---
    private static class BurstParticle {
        float x, y;
        float vx, vy;
        float angle;
        float life;
        float maxLife;
        float rotationSpeed;

        public BurstParticle(float x, float y) {
            this.x = x;
            this.y = y;
            this.life = 0;
            this.maxLife = com.badlogic.gdx.math.MathUtils.random(1.0f, 1.5f); // Lasts 1.0-1.5s

            // Velocity: Shot backwards (Left) with spread
            float speed = com.badlogic.gdx.math.MathUtils.random(200f, 400f);
            float angleDeg = com.badlogic.gdx.math.MathUtils.random(85f, 275f); // Backwards +/- 60 deg
            this.vx = com.badlogic.gdx.math.MathUtils.cosDeg(angleDeg) * speed;
            this.vy = com.badlogic.gdx.math.MathUtils.sinDeg(angleDeg) * speed;

            this.angle = com.badlogic.gdx.math.MathUtils.random(360f);
            this.rotationSpeed = com.badlogic.gdx.math.MathUtils.random(90f, 270f); // Spin left
        }
    }

    private final com.badlogic.gdx.utils.Array<BurstParticle> burstParticles = new com.badlogic.gdx.utils.Array<>();

    // --- HUD / UI ---
    private OrthographicCamera uiCamera;
    private float survivalTime = 0;
    private float scoreTime = 0;

    // --- WAVE MODE ---
    private boolean waveModeActive = false;
    private float waveModeTimer = 0;
    private float exitShakeTimer = 0; // New Shake Timer for Exit
    private int clickComboCount = 0;
    private float lastClickTime = 0;
    private float waveModeDuration = 7.0f;
    private float comboTimeWindow = 0.2f; // Reset combo if > 0.2s between clicks
    private float scoreBonus = 0.25f; // Bonus per zigzag in Wave Mode
    private float scoreScale = 1.0f; // For text pop effect

    // --- RHYTHMIC PULSE (160 BPM) ---
    private final float bpm = 160f;
    private final float beatInterval = 60f / bpm; // Seconds per beat
    private float beatTimer = 0;
    private float pulseTimer = 0;
    private final float pulseDuration = 0.1f; // Quick decay as requested
    private final float pulseIntensity = 0.05f; // Zoom amount (0.7 -> 0.65)

    private void triggerStartBurst() {
        for (int i = 0; i < 20; i++) { // Spawn 20 particles
            // Spawn in a semi-circle behind the head
            burstParticles.add(new BurstParticle(playerPosition.x - 10, playerPosition.y));
        }
    }

    public GameScreen(MainGame game) {
        this.game = game;
        // Initialize with Legacy Mode and Wave Difficulty for now
        this.levelGenerator = new LegacyLevelGenerator(GameManager.getInstance().getDifficulty());
        GameManager.getInstance().setGameState(GameManager.GameState.RUNNING);
    }

    private com.badlogic.gdx.graphics.g2d.SpriteBatch batch;
    private com.badlogic.gdx.graphics.g2d.BitmapFont font;

    @Override
    public void show() {
        camera = new OrthographicCamera();
        camera.setToOrtho(false, 800, 480);
        camera.zoom = 0.7f; // Zoom in to focus on the visible band (height 300 vs 480 screen)

        uiCamera = new OrthographicCamera();
        uiCamera.setToOrtho(false, 800, 480); // Independent UI Camera (No Zoom/Shake)

        shapeRenderer = new ShapeRenderer();
        batch = new com.badlogic.gdx.graphics.g2d.SpriteBatch();

        survivalTime = 0;
        scoreTime = 0;

        // Try to load custom font
        try {
            com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator generator = new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator(
                    Gdx.files.internal("fonts/ginzanarrow-heavy.otf"));
            com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter parameter = new com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter();
            parameter.size = 24;
            parameter.color = Color.WHITE;
            font = generator.generateFont(parameter);
            generator.dispose();
        } catch (Exception e) {
            // Fallback
            font = new com.badlogic.gdx.graphics.g2d.BitmapFont();
            font.setColor(Color.WHITE);
            font.getData().setScale(1.5f);
            System.out.println("Could not load custom font, using default: " + e.getMessage());
        }

        playerPosition = new Vector2(100, 240);
        trail = new Array<>();
        trail.add(new Vector2(playerPosition));

        triggerStartBurst(); // Trigger immediately on restart (Horizontal phase)
    }

    // ... (rest of file)

    // ... (rest of file)

    private boolean hasStarted = false;
    private float inputDelayTimer = 0.01f; // Ignore input for 0.2s to prevent "Restart Click" from starting the game
                                           // immediately

    @Override
    public void render(float delta) {
        // Update logic
        if (GameManager.getInstance().getGameState() == GameManager.GameState.RUNNING) {

            if (inputDelayTimer > 0) {
                inputDelayTimer -= delta;
            }

            // Check for game start (first click)
            if (!hasStarted && inputDelayTimer <= 0) {
                if (Gdx.input.justTouched()) { // Use justTouched for crisp start
                    hasStarted = true;
                    // First click implies going UP
                    currentDirection = 1;
                    trail.add(new Vector2(playerPosition)); // Add corner point where we started going up
                }
            }

            // Input handling (Normal Gameplay)
            if (hasStarted) {
                int targetDirection = -1; // Default to falling

                // If touching, go up
                if (Gdx.input.isTouched()) {
                    targetDirection = 1;
                }

                if (targetDirection != currentDirection) {
                    trail.add(new Vector2(playerPosition)); // Add corner point
                    currentDirection = targetDirection;

                    // --- WAVE MODE LOGIC ---
                    if (waveModeActive) {
                        // Bonus Score
                        scoreTime += scoreBonus;
                        scoreScale = 1.5f; // Pop effect
                    } else {
                        // Combo Check
                        float timeSinceClick = survivalTime - lastClickTime;
                        if (timeSinceClick <= comboTimeWindow) {
                            clickComboCount++;
                        } else {
                            clickComboCount = 1; // Reset or Start new
                        }

                        lastClickTime = survivalTime;

                        if (clickComboCount >= 16) {
                            // ACTIVATE WAVE MODE
                            waveModeActive = true;
                            waveModeTimer = waveModeDuration;
                            clickComboCount = 0;
                            // Trigger Visuals (Camera Shake / Invert)
                            // Camera Twist
                            camera.rotate(5f); // Instant Twist-Back handled in update? No, let's just create a shake.
                            // Actually, user asked for "Twist transversal and vertical"
                            // We can simulate this with a violent shake or using `currentWobble`
                            if (levelGenerator instanceof LegacyLevelGenerator) {
                                ((LegacyLevelGenerator) levelGenerator).setWobbleAngle(10f); // Massive wobble kick
                            }
                        }
                    }
                }

                // Apply Vertical movement
                playerPosition.y += verticalSpeed * currentDirection * delta;
            }

            // Allow Horizontal movement always (wave moves forward)
            playerPosition.x += horizontalSpeed * delta;

            // Collision check is now done at end of frame using NOSE position

            // Trail logic (Only update when running)
            timeSinceLastTrailPoint += delta;
            if (timeSinceLastTrailPoint >= trailInterval) {
                trail.add(new Vector2(playerPosition));
                timeSinceLastTrailPoint = 0;
                // Keep trail limited length if needed, for now let it grow or limit count
                if (trail.size > 50) {
                    trail.removeIndex(0);
                }
            }
        } else {
            // Game Over / Frozen state logic (no movement, no input)
        }

        // Camera follow
        camera.position.x = playerPosition.x + 200; // Keep player slightly to the left
        camera.position.y = playerPosition.y; // Strict vertical follow

        // --- EXIT SHAKE LOGIC (Running State) ---
        if (exitShakeTimer > 0) {
            exitShakeTimer -= delta;
            float intensity = 15f * (exitShakeTimer / 1.0f); // 1s Shake, 15px Intensity (Increased)
            float dx = com.badlogic.gdx.math.MathUtils.random(-intensity, intensity);
            float dy = com.badlogic.gdx.math.MathUtils.random(-intensity, intensity);
            camera.translate(dx, dy);
        }

        // --- SCREEN SHAKE & HIT STOP LOGIC ---
        // Apply offset AFTER locking to player position, but BEFORE rendering.
        if (GameManager.getInstance().getGameState() == GameManager.GameState.GAME_OVER) {

            // Hit Stop Timer (Freeze Frame)
            if (hitStopTimer > 0) {
                hitStopTimer -= delta;
                if (hitStopTimer <= 0) {
                    // --- TRIGGER CRASH EFFECTS NOW ---
                    // 1. Instant Visual Reset
                    if (levelGenerator instanceof LegacyLevelGenerator) {
                        ((LegacyLevelGenerator) levelGenerator).resetWobble();
                        ((LegacyLevelGenerator) levelGenerator).ENABLE_ASSEMBLY_FX = false; // Snap grid (Stop assembly
                                                                                            // drift)
                        ((LegacyLevelGenerator) levelGenerator).ENABLE_GLITCH_FX = true; // Start Paper Turn Glitch
                    }

                    // 2. Impact Zoom
                    camera.zoom = 0.5f;
                    camera.position.set(crashPoint.x, crashPoint.y, 0);
                    camera.update(); // Update immediately for Shake to use correct base

                    // 3. Start Shake
                    shakeTimer = 0.5f;
                }
            }

            // Shake Logic (Only if Hit Stop is finished)
            if (shakeTimer > 0 && hitStopTimer <= 0) {
                shakeTimer -= delta;
                float intensity = 10f * (shakeTimer / 0.5f); // Fade out
                float dx = com.badlogic.gdx.math.MathUtils.random(-intensity, intensity);
                float dy = com.badlogic.gdx.math.MathUtils.random(-intensity, intensity);
                camera.translate(dx, dy);
            }
        }

        // Elastic Camera Wobble Logic
        if (GameManager.getInstance().getGameState() == GameManager.GameState.RUNNING && isWobbleActive) {
            wobbleTimer += delta;

            // Cycle Duration: 3 seconds. T = 3. Freq = 1/3.
            // Omega = 2 * PI * Freq = 2 * PI / 3.
            float angle = (float) Math.sin(wobbleTimer * (Math.PI * 2 / 3.0f)) * MAX_WOBBLE_ANGLE;

            // Apply Rotation (Reset first to avoid accumulation)
            camera.up.set(0, 1, 0);
            camera.direction.set(0, 0, -1);
            camera.rotate(angle);

            // Zoom Compensation (Bubble Stretch)
            // Base Zoom is 0.7f.
            // Shrink zoom (zoom in) as angle increases to 10 deg.
            // Factor: 0.005 per degree.
            // At 10 deg: 0.05. New Zoom = 0.65.
            camera.zoom = 0.7f - (Math.abs(angle) * 0.005f);

            // --- RHYTHMIC PULSE LOGIC ---
            // Update Beat Timer
            beatTimer += delta;
            if (beatTimer >= beatInterval) {
                beatTimer -= beatInterval;
                pulseTimer = pulseDuration; // Trigger Pulse
            }

            // Update Pulse Timer & Apply Zoom
            if (pulseTimer > 0) {
                pulseTimer -= delta;
                if (pulseTimer < 0)
                    pulseTimer = 0;

                // Interpolate Scale (1.0 -> 0.0)
                float pulseProgress = pulseTimer / pulseDuration;
                // Cubic Fade out for "Sharp Attack, Smooth Release"
                // Actually linear is fine for 0.1s, maybe square it.
                float currentPulse = pulseIntensity * pulseProgress;

                // Apply to Zoom (Zoom In = Decrease Value)
                camera.zoom -= currentPulse;
            }

        } else {
            // Frozen State: Force Rotation to 0. Zoom stays frozen (set in checkCollision
            // to 0.5f).
            camera.up.set(0, 1, 0);
            camera.direction.set(0, 0, -1);
        }

        camera.update();

        // Update Level Generator
        // Update Level Generator
        if (levelGenerator instanceof LegacyLevelGenerator) {
            ((LegacyLevelGenerator) levelGenerator).update(delta, camera.position.x, camera.position.y);
            // Sync Wave Mode State EARLY
            ((LegacyLevelGenerator) levelGenerator).setWaveMode(waveModeActive);
        } else {
            levelGenerator.update(delta, camera.position.x);
        }

        // Pass Wobble for Geometic Shear
        if (levelGenerator instanceof LegacyLevelGenerator) {
            ((LegacyLevelGenerator) levelGenerator).setWobbleAngle(getWobbleAngle());
        }

        // Render
        // Gdx.gl.glClearColor(0.2f, 0.2f, 0.2f, 1); // Redundant, renderLevelBase
        // handles clear
        // Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 1. Draw Level Mask (White Background/Bars) - NOW FIRST
        levelGenerator.renderLevelMask(shapeRenderer, camera);

        // 2. Draw Level Base (Grid + Assembly FX) - NOW SECOND (On Top)
        levelGenerator.renderLevelBase(shapeRenderer, camera);

        // 3. Draw Obstacles
        levelGenerator.renderObstacles(shapeRenderer, camera);

        // Draw Player
        // Match color to obstacles (Dark Color) as requested
        if (waveModeActive) {
            shapeRenderer.setColor(Color.WHITE);
        } else {
            if (levelGenerator instanceof LegacyLevelGenerator) {
                shapeRenderer.setColor(((LegacyLevelGenerator) levelGenerator).getObstacleColor());
            } else {
                shapeRenderer.setColor(Color.WHITE);
            }
        }

        float lineWidth = 5f; // Width of the trail

        // Calculate vertical offset for the ribbon thickness
        float speed = new Vector2(horizontalSpeed, verticalSpeed).len();
        float vxNorm = horizontalSpeed / speed;
        // Match trail width to triangle base width (2 * lineWidth)
        // h = R / vxNorm, where R is half-width = lineWidth
        float verticalHalfWidth = lineWidth / vxNorm;

        // Draw trail
        if (trail.size > 0) {
            float shearAngle;
            if (levelGenerator instanceof LegacyLevelGenerator) {
                shearAngle = ((LegacyLevelGenerator) levelGenerator).getShearAngle();
            } else {
                shearAngle = getWobbleAngle();
            }
            float shearK = 0.055f * shearAngle;

            // Set Color for Trail (Matches Player/Obstacles)
            if (waveModeActive) {
                shapeRenderer.setColor(Color.WHITE);
            } else {
                if (levelGenerator instanceof LegacyLevelGenerator) {
                    // FORCE MATCH OBSTACLE COLOR
                    shapeRenderer.setColor(((LegacyLevelGenerator) levelGenerator).getObstacleColor());
                } else {
                    shapeRenderer.setColor(Color.valueOf("66CCAA")); // Fallback for other generators
                }
            }

            for (int i = 0; i < trail.size - 1; i++) {
                Vector2 p1 = trail.get(i);
                Vector2 p2 = trail.get(i + 1);

                // Apply Geometric Shear (Match Grid)
                float shear1 = (p1.y - camera.position.y) * shearK;
                float shear2 = (p2.y - camera.position.y) * shearK;

                float x1 = p1.x + shear1;
                float y1_top = p1.y + verticalHalfWidth;
                float y1_bot = p1.y - verticalHalfWidth;

                float x2 = p2.x + shear2;
                float y2_top = p2.y + verticalHalfWidth;
                float y2_bot = p2.y - verticalHalfWidth;

                shapeRenderer.triangle(x1, y1_top, x1, y1_bot, x2, y2_top);
                shapeRenderer.triangle(x2, y2_top, x1, y1_bot, x2, y2_bot);
            }
            // Line from last trail point to player
            Vector2 lastTrail = trail.get(trail.size - 1);
            float shearLast = (lastTrail.y - camera.position.y) * shearK;

            float x1 = lastTrail.x + shearLast;
            float y1_top = lastTrail.y + verticalHalfWidth;
            float y1_bot = lastTrail.y - verticalHalfWidth;

            float shearPlayer = (playerPosition.y - camera.position.y) * shearK;
            float x2 = playerPosition.x + shearPlayer;
            float y2_top = playerPosition.y + verticalHalfWidth;
            float y2_bot = playerPosition.y - verticalHalfWidth;

            shapeRenderer.triangle(x1, y1_top, x1, y1_bot, x2, y2_top);
            shapeRenderer.triangle(x2, y2_top, x1, y1_bot, x2, y2_bot);
        }

        // --- RENDER BURST PARTICLES ---
        if (burstParticles.size > 0) {
            Color pColor = Color.WHITE; // Default
            if (levelGenerator instanceof LegacyLevelGenerator) {
                pColor = ((LegacyLevelGenerator) levelGenerator).getObstacleColor();
            }
            shapeRenderer.setColor(pColor);

            for (int i = burstParticles.size - 1; i >= 0; i--) {
                BurstParticle p = burstParticles.get(i);
                p.life += delta;
                if (p.life >= p.maxLife) {
                    burstParticles.removeIndex(i);
                    continue;
                }

                // Update Physics
                p.x += p.vx * delta;
                p.y += p.vy * delta;
                p.angle += p.rotationSpeed * delta;

                // Scale down with life
                float scale = 1f - (p.life / p.maxLife);
                float size = 30f * scale;

                // Simple Triangle Particle
                // Rotate points
                float halfS = size / 2f;
                float height = size * 0.866f; // Equilateral height relative to side

                // Local coords
                float x1 = -halfS, y1 = -height / 3f;
                float x2 = halfS, y2 = -height / 3f;
                float x3 = 0, y3 = 2f * height / 3f;

                // Rotate & Translate
                float cos = com.badlogic.gdx.math.MathUtils.cosDeg(p.angle);
                float sin = com.badlogic.gdx.math.MathUtils.sinDeg(p.angle);

                float wx1 = p.x + (x1 * cos - y1 * sin);
                float wy1 = p.y + (x1 * sin + y1 * cos);
                float wx2 = p.x + (x2 * cos - y2 * sin);
                float wy2 = p.y + (x2 * sin + y2 * cos);
                float wx3 = p.x + (x3 * cos - y3 * sin);
                float wy3 = p.y + (x3 * sin + y3 * cos);

                // Apply Camera Shear (Matches world)
                if (isWobbleActive) { // Only shear if game not frozen
                    // We need current wobble. existing getWobbleAngle() helper?
                    // I'll assume 0 here or reuse shearK from above if defined nearby
                    // Actually, particle should exist in WORLD space so camera shear applies
                    // visually?
                    // No, "wobble" is a vertex shader-like effect applied manually here.
                    // Let's skip shear for particles to keep them "chaotic" or apply simple
                    // consistent shear.
                    // Applying simple shear:
                    float sK = 0.055f * getWobbleAngle();
                    wx1 += (wy1 - camera.position.y) * sK;
                    wx2 += (wy2 - camera.position.y) * sK;
                    wx3 += (wy3 - camera.position.y) * sK;
                }

                shapeRenderer.triangle(wx1, wy1, wx2, wy2, wx3, wy3);
            }
        }
        // Draw Head (Triangle)
        // Use the same vertical offsets as the trail to ensure seamless connection
        // Base of the triangle is the vertical line at playerPosition
        float headX = playerPosition.x;
        float baseTopY = playerPosition.y + verticalHalfWidth;
        float baseBotY = playerPosition.y - verticalHalfWidth;

        // Nose is projected along velocity
        // Make the head length shorter (e.g. 2.5x lineWidth) to be less pointy
        Vector2 velocity;
        if (!hasStarted) {
            velocity = new Vector2(horizontalSpeed, 0).nor(); // Flat velocity
        } else {
            velocity = new Vector2(horizontalSpeed, verticalSpeed * currentDirection).nor();
        }
        Vector2 nose = new Vector2(velocity).scl(lineWidth * 2.5f).add(playerPosition);

        shapeRenderer.triangle(nose.x, nose.y, headX, baseTopY, headX, baseBotY);

        // Draw Obstacles (Moved to before Mask)
        // levelGenerator.renderObstacles(shapeRenderer, camera);

        shapeRenderer.end();

        // --- LOGIC UPDATES AFTER RENDER TO USE CALCULATED VALUES ---
        // Check Collision using the NOSE (Tip) position for instant impact feel
        if (GameManager.getInstance().getGameState() == GameManager.GameState.RUNNING) {
            if (levelGenerator.checkCollision(nose)) {
                GameManager.getInstance().setGameState(GameManager.GameState.GAME_OVER);

                // Initiate Hit Stop
                hitStopTimer = 0.2f;
                crashPoint.set(nose); // Store for Zoom later

                // Do NOT apply effects yet. They trigger when hitStopTimer <= 0.
            }
        }

        // --- MANUAL RESTART LOGIC ---
        if (GameManager.getInstance().getGameState() == GameManager.GameState.GAME_OVER && hitStopTimer <= 0) {
            // Wait for Click to Restart
            // Only restart if Shake is done
            if (shakeTimer <= 0 && com.badlogic.gdx.Gdx.input.justTouched()) {
                game.setScreen(new GameScreen(game));
            }
        }

        // --- OUTER COLLISION FEEDBACK ---
        // If Game Over AND it was an Outer Collision, render the specific obstacle ON
        // TOP of the mask
        if (GameManager.getInstance().getGameState() == GameManager.GameState.GAME_OVER && hitStopTimer <= 0)

        {
            if (levelGenerator instanceof LegacyLevelGenerator) {
                LegacyLevelGenerator legacyGen = (LegacyLevelGenerator) levelGenerator;
                if (legacyGen.wasLastCollisionOuter()) {
                    // 1. Draw the specific obstacle on top (reveal it)
                    shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
                    legacyGen.renderCollidedObstacle(shapeRenderer);

                    // 2. Draw Parallelogram Background for Text
                    // Floating slightly to the right of the tip
                    String text = "OUTER COLLISION";
                    com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout(
                            font, text);

                    float textWidth = layout.width;
                    float textHeight = layout.height;

                    float padX = 15f; // Horizontal padding
                    float padY = 10f; // Vertical padding

                    float boxWidth = textWidth + (padX * 2);
                    float boxHeight = textHeight + (padY * 2); // Height of the box
                    float skew = 20f; // horizontal skew

                    // Positioning Logic
                    // Horizontal: Center of message aligned with Nose Tip (nose.x)
                    // Box Center X = nose.x
                    // Box Left (textX) = nose.x - (boxWidth / 2) - (skew / 2) ???
                    // Let's refine Geometric Center X = (BL.x + TR.x) / 2 = (textX + textX +
                    // boxWidth + skew) / 2 = textX + boxWidth/2 + skew/2
                    // We want Geometric Center X = nose.x
                    // textX = nose.x - boxWidth/2 - skew/2

                    float textX = nose.x - (boxWidth / 2) - (skew / 2);

                    // Vertical: Above or Below head based on collision type
                    float clearance = 30f; // Distance from nose to box edge

                    float boxBottom, boxTop;

                    if (legacyGen.wasLastCollisionCeiling()) {
                        // Hit Ceiling -> Message ABOVE head
                        // boxBottom should be at nose.y + clearance
                        boxBottom = nose.y + clearance;
                        boxTop = boxBottom + boxHeight;
                        // Text drawing Y? font.draw draws at baseline.
                        // Center Y of box is (boxBottom + boxTop) / 2
                        // drawY = CenterY + textHeight/2
                    } else {
                        // Hit Floor -> Message BELOW head
                        // boxTop should be at nose.y - clearance
                        boxTop = nose.y - clearance;
                        boxBottom = boxTop - boxHeight;
                    }

                    shapeRenderer.setColor(Color.BLACK);
                    // Draw Parallelogram (Quad) using triangles
                    // Box coordinates without skew:
                    // BL: (textX, boxBottom)
                    // TL: (textX, boxTop)
                    // TR: (textX + boxWidth, boxTop)
                    // BR: (textX + boxWidth, boxBottom)

                    // With Skew (shift top right):
                    float bl_x = textX;
                    float tl_x = textX + skew;
                    float tr_x = textX + boxWidth + skew;
                    float br_x = textX + boxWidth;

                    shapeRenderer.triangle(bl_x, boxBottom, tl_x, boxTop, tr_x, boxTop);
                    shapeRenderer.triangle(bl_x, boxBottom, tr_x, boxTop, br_x, boxBottom);

                    shapeRenderer.end();

                    // 3. Draw Text "OUTER COLLISION"
                    batch.setProjectionMatrix(camera.combined);
                    batch.begin();

                    float drawX = textX + (skew / 2) + padX;
                    float drawY = boxBottom + (boxHeight / 2) + (textHeight / 2);

                    font.draw(batch, text, drawX, drawY);
                    batch.end();
                }
            }
        }

        // --- HUD / UI (FIXED) ---
        // Update Timers
        if (GameManager.getInstance().getGameState() == GameManager.GameState.RUNNING) { // Timers start immediately
            survivalTime += delta;
            scoreTime += delta;

            // Wave Mode Timer
            if (waveModeActive) {
                waveModeTimer -= delta;
                if (waveModeTimer <= 0) {
                    waveModeActive = false;
                    exitShakeTimer = 1.0f; // Start 1s Shake
                    // Reset Visuals logic will be in LevelGenerator
                }
            }

            // Score Scale Decay
            if (scoreScale > 1.0f) {
                scoreScale -= 5f * delta; // Quick recover (0.1s approx)
                if (scoreScale < 1.0f)
                    scoreScale = 1.0f;
            }
        }

        // batch.setProjectionMatrix(uiCamera.combined); // Not needed here yet
        // batch.begin(); // REMOVED PREMATURE BEGIN

        String scoreText = String.format(java.util.Locale.US, "%.3f", scoreTime);
        String survivalText = String.format(java.util.Locale.US, "%.3f", survivalTime);

        // Scale Score Text
        font.getData().setScale(1.2f * scoreScale);
        com.badlogic.gdx.graphics.g2d.GlyphLayout layoutVideo = new com.badlogic.gdx.graphics.g2d.GlyphLayout(font,
                scoreText);

        font.getData().setScale(1.2f); // Reset for Survival Text
        com.badlogic.gdx.graphics.g2d.GlyphLayout layoutReal = new com.badlogic.gdx.graphics.g2d.GlyphLayout(font,
                survivalText); // Moved up

        float marginX = 20;
        float marginY = 20;
        float screenW = uiCamera.viewportWidth;
        float screenH = uiCamera.viewportHeight;

        // Calculate layout size to determine box width
        float maxTextWidth = Math.max(layoutVideo.width, layoutReal.width);
        float boxWidth = maxTextWidth + marginX * 3; // Extra padding
        float boxHeight = 100f;

        // Parallelogram Geometry (Slanted \)
        // Top Left X
        float boxRightX = screenW + 50; // Extend off-screen
        float boxTopY = screenH;
        float boxBotY = screenH - boxHeight;

        float skew = 40f; // Slant amount
        // We want the left edge to slant down-right (\)
        // So Top-Left is further Left than Bottom-Left?
        // Wait, "Down to the Right". \ . Top is Left, Bottom is Right.
        // TL x = Base. BL x = Base + skew.

        float boxLeftBase = screenW - boxWidth;

        // Vertices
        float x1 = boxLeftBase; // Top Left
        float y1 = boxTopY;
        float x2 = boxRightX; // Top Right
        float y2 = boxTopY;
        float x3 = boxRightX + skew; // Bottom Right (Shifted Right)
        float y3 = boxBotY;
        float x4 = boxLeftBase + skew; // Bottom Left (Shifted Right)
        float y4 = boxBotY;

        // Draw Background
        shapeRenderer.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        if (waveModeActive)
            shapeRenderer.setColor(Color.WHITE);
        else
            shapeRenderer.setColor(Color.BLACK);
        shapeRenderer.triangle(x1, y1, x2, y2, x4, y4);
        shapeRenderer.triangle(x2, y2, x3, y3, x4, y4);
        shapeRenderer.end();

        // Draw Text
        batch.setProjectionMatrix(uiCamera.combined);
        batch.begin();

        if (waveModeActive)
            font.setColor(Color.BLACK);
        else
            font.setColor(Color.WHITE);

        font.getData().setScale(1.2f * scoreScale); // Apply Pop
        font.draw(batch, scoreText, screenW - layoutVideo.width - marginX, screenH - marginY);

        font.getData().setScale(1.2f); // Reset
        font.draw(batch, survivalText, screenW - layoutReal.width - marginX, screenH - marginY - 40);

        font.setColor(Color.WHITE); // Reset for next frame

        batch.end();
    }

    @Override
    public void resize(int width, int height) {
        camera.viewportWidth = width;
        camera.viewportHeight = height;
        camera.update();

        uiCamera.viewportWidth = width;
        uiCamera.viewportHeight = height;
        uiCamera.position.set(width / 2f, height / 2f, 0); // Center camera on screen center
        uiCamera.update();
    }

    @Override
    public void dispose() {
        shapeRenderer.dispose();
        if (batch != null)
            batch.dispose();
        if (font != null)
            font.dispose();
    }

    public float getWobbleAngle() {
        if (!isWobbleActive || GameManager.getInstance().getGameState() != GameManager.GameState.RUNNING)
            return 0;
        return (float) Math.sin(wobbleTimer * (Math.PI * 2 / 3.0f)) * MAX_WOBBLE_ANGLE;
    }
}
