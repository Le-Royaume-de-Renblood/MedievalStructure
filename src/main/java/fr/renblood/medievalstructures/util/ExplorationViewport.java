package fr.renblood.medievalstructures.util;

/** One coordinate transform for drawing, buttons, tooltips and pointer events. */
public record ExplorationViewport(float scale, int width, int height) {
    public static ExplorationViewport fit(int screenWidth, int screenHeight) {
        float scale=Math.min(1F,Math.min(Math.max(1,screenWidth-16)/560F,Math.max(1,screenHeight-16)/380F));
        return new ExplorationViewport(scale,(int)Math.ceil(screenWidth/scale),(int)Math.ceil(screenHeight/scale));
    }
    public double logical(double coordinate) {return coordinate/scale;}
}
