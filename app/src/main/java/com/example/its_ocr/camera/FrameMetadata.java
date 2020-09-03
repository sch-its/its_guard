package com.example.its_ocr.camera;

public class FrameMetadata {
    private final int width;
    private final int height;
    private final int rotation;

    private FrameMetadata(int width, int height, int rotation) {
        this.width = width;
        this.height = height;
        this.rotation = rotation;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public int getRotation() {
        return rotation;
    }

    /**
     * Builder of {@link FrameMetadata}.
     */
    public static class Builder {

        private int width;
        private int height;
        private int rotation;

        public Builder setWidth(int width) {
            this.width = width;
            return this;
        }

        public Builder setHeight(int height) {
            this.height = height;
            return this;
        }

        public Builder setRotation(int rotation) {
            this.rotation = rotation;
            return this;
        }

        public FrameMetadata build() {
            return new FrameMetadata(width, height, rotation);
        }
    }
}
