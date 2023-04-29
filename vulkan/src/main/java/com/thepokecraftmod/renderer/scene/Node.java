package com.thepokecraftmod.renderer.scene;

import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public class Node {

    private final List<Node> children;

    private final String name;

    private final Node parent;

    private final Matrix4f nodeTransformation;

    public Node(String name, Node parent, Matrix4f nodeTransformation) {
        this.name = name;
        this.parent = parent;
        this.nodeTransformation = nodeTransformation;
        this.children = new ArrayList<>();
    }

    public void addChild(Node node) {
        this.children.add(node);
    }

    public List<Node> getChildren() {
        return this.children;
    }

    public String getName() {
        return this.name;
    }

    public Matrix4f getNodeTransformation() {
        return this.nodeTransformation;
    }

    public Node getParent() {
        return this.parent;
    }
}
