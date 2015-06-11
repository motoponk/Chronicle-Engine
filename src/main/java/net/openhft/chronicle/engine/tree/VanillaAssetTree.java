package net.openhft.chronicle.engine.tree;

import net.openhft.chronicle.engine.api.tree.Asset;
import net.openhft.chronicle.engine.api.tree.AssetNotFoundException;
import net.openhft.chronicle.engine.api.tree.AssetTree;
import net.openhft.chronicle.engine.api.tree.RequestContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created by peter on 22/05/15.
 */
public class VanillaAssetTree implements AssetTree {
    final VanillaAsset root = new VanillaAsset(null, "");

    public VanillaAssetTree() {

    }

    @NotNull
    public VanillaAssetTree forTesting() {
        root.forTesting();
        return this;
    }

    @NotNull
    public VanillaAssetTree forRemoteAccess() {
        root.forRemoteAccess();
        return this;
    }

    @NotNull
    @Override
    public <A> Asset acquireAsset(Class<A> assetClass, @NotNull RequestContext context) throws AssetNotFoundException {
        String fullName = context.fullName();
        if (fullName.startsWith("/"))
            fullName = fullName.substring(1);
        return fullName.isEmpty() ? root : root.acquireAsset(context, fullName);
    }

    @Nullable
    @Override
    public Asset getAsset(@NotNull String fullName) {
        if (fullName.startsWith("/"))
            fullName = fullName.substring(1);
        return fullName.isEmpty() ? root : root.getAsset(fullName);
    }

    @Override
    public Asset root() {
        return root;
    }

    @Override
    public void close() {
        root.close();
    }
}
