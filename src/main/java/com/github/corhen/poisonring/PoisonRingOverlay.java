package com.github.corhen.poisonring;

import java.awt.BasicStroke;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class PoisonRingOverlay extends Overlay {
    // Same anchor as the core Regen Meter's ring: its 26px circle starts 27px into the orb widget,
    // so the orb's centre sits 40px in, vertically centred.
    private static final int CENTER_X_OFFSET = 40;
    // Sits just outside the Regen Meter's ring so both can show at once.
    private static final int BASE_DIAMETER = 32;

    private final Client client;
    private final PoisonRingPlugin plugin;
    private final PoisonRingConfig config;

    @Inject
    public PoisonRingOverlay(Client client, PoisonRingPlugin plugin, PoisonRingConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!plugin.isPoisoned()) {
            return null;
        }

        Widget healthOrb = client.getWidget(InterfaceID.Orbs.ORB_HEALTH);
        if (healthOrb == null || healthOrb.isHidden()) {
            healthOrb = client.getWidget(InterfaceID.OrbsNomap.ORB_HEALTH);
        }
        if (healthOrb == null || healthOrb.isHidden()) {
            return null;
        }

        Rectangle bounds = healthOrb.getBounds();

        // Diameter grows and shrinks around the orb's centre, then the shifts move the whole ring.
        // The box is kept on whole pixels, like the Regen Meter's, so the anti-aliased edge stays
        // even all the way round instead of sharp on one side and smeared on the other.
        int diameter = Math.max(1, BASE_DIAMETER + config.diameter());
        int x = bounds.x + CENTER_X_OFFSET + config.shiftX() - diameter / 2;
        int y = bounds.y + bounds.height / 2 + config.shiftY() - diameter / 2;
        // An odd thickness straddles the path, so move the path onto pixel centres to keep it crisp
        double align = config.lineThickness() % 2 == 1 ? 0.5 : 0;

        double progress = (double) plugin.getTicksUntilDamage() / plugin.getPoisonTickRate();

        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics.setColor(plugin.isVenom() ? config.venomColor() : config.poisonColor());
        // Butt caps so the arc's ends stop exactly where the arc does, no square nub past them
        graphics.setStroke(new BasicStroke(config.lineThickness(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));

        Arc2D.Double arc = new Arc2D.Double(
            x + align,
            y + align,
            diameter,
            diameter,
            90,
            -360 * progress,
            Arc2D.OPEN
        );

        graphics.draw(arc);

        return null;
    }
}
