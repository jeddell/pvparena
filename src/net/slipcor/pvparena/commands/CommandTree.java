package net.slipcor.pvparena.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandTree<T> {
    private final T key;
    private final List<CommandTree<T>> children = new ArrayList<CommandTree<T>>();

    public CommandTree(final T key) {
        this.key = key;
    }

    public List<T> getContent() {
        List<T> result = new ArrayList<T>();
        for (CommandTree<T> tree : children) {
            result.add(tree.key);
        }
        return result;
    }

    public void define(final T[] args) {
        if (args.length > 1) {
            CommandTree<T> tree;
            if (contains(args[0])) {
                tree = get(args[0]);
            } else {
                tree = new CommandTree<T>(args[0]);
                children.add(tree);
            }
            tree.define(Arrays.copyOfRange(args, 1, args.length));
        } else {
            // one argument left
            if (contains(args[0])) {
                // child exists; better remove
                remove(args[0]);
            }
            children.add(new CommandTree<T>(args[0]));
        }
    }

    public boolean contains(final T key) {
        for (CommandTree<T> tree : children) {
            if (tree.key.equals(key)) {
                return true;
            }
            if (tree.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public CommandTree<T> get(final T key) {
        for (final CommandTree<T> tree : children) {
            if (tree.key.equals(key)) {
                return tree;
            }
            if (tree.contains(key)) {
                return tree.get(key);
            }
        }
        return null;
    }

    private boolean remove(final T key) {
        for (final CommandTree<T> tree : children) {
            if (tree.key.equals(key)) {
                children.remove(tree);
                return true;
            }
            if (tree.contains(key)) {
                return tree.remove(key);
            }
        }
        return false;
    }
/*
    public void debug(final int level) {
        final StringBuffer c = new StringBuffer();
        for (int pos=0; pos<level; pos++) {
            c.append('-');
        }
        System.out.println("");
        System.out.println("-----------");
        System.out.print(c.toString()+"key:");
        System.out.println(key);
        System.out.print(c.toString()+"children:");
        for (final CommandTree<T> child : children) {
            child.debug(level+1);
        }
    }*/
}