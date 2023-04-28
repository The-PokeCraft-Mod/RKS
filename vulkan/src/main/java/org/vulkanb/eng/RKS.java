package org.vulkanb.eng;

import imgui.ImGui;
import org.vulkanb.eng.impl.Render;
import org.vulkanb.eng.impl.gui.GuiRenderActivity;
import org.vulkanb.eng.scene.Scene;

public class RKS {

    private final RenderingImpl impl;
    private final Render render;
    private final Scene scene;
    private final Window window;
    private boolean running;

    public RKS(RenderingImpl impl, Window window) {
        this.impl = impl;
        this.window = window;
        this.scene = new Scene(this.window);
        this.render = new Render(this.window, this.scene);
        impl.init(this.scene, this.render);
    }

    private void cleanup() {
        this.impl.close();
        this.render.close();
        this.window.close();
    }

    private boolean handleInputGui() {
        var imGuiIO = ImGui.getIO();
        var mouseInput = this.window.getMouseInput();
        var mousePos = mouseInput.getCurrentPos();
        imGuiIO.setMousePos(mousePos.x, mousePos.y);
        imGuiIO.setMouseDown(0, mouseInput.isLeftButtonPressed());
        imGuiIO.setMouseDown(1, mouseInput.isRightButtonPressed());

        return imGuiIO.getWantCaptureMouse() || imGuiIO.getWantCaptureKeyboard();
    }

    public void run() {
        var settings = Settings.getInstance();
        var initialTime = System.nanoTime();
        var timeU = 1000000000d / settings.getUps();
        double deltaU = 0;

        var updateTime = initialTime;
        while (this.running && !this.window.shouldClose()) {

            this.scene.getCamera().setHasMoved(false);
            this.window.pollEvents();

            var currentTime = System.nanoTime();
            deltaU += (currentTime - initialTime) / timeU;
            initialTime = currentTime;

            if (deltaU >= 1) {
                var diffTimeNanos = currentTime - updateTime;
                var inputConsumed = handleInputGui();
                this.impl.handleInput(this.window, this.scene, diffTimeNanos, inputConsumed);
                updateTime = currentTime;
                deltaU--;
            }

            this.render.render(this.window, this.scene);
        }

        cleanup();
    }

    public void start() {
        this.running = true;
        run();
    }

    public void stop() {
        this.running = false;
    }
}
