package com.scaperune.mirror;

import java.util.function.Consumer;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class ScapeRunePanel extends PluginPanel
{
    private final JCheckBox toggle = new JCheckBox("Mirror world");
    private final JLabel status = new JLabel();

    ScapeRunePanel(Consumer<Boolean> onToggle)
    {
        JLabel title = new JLabel("ScapeRune");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setForeground(new Color(235, 191, 92));
        add(title);
        JLabel subtitle = new JLabel("GPU world mirror");
        subtitle.setForeground(Color.LIGHT_GRAY);
        add(subtitle);
        toggle.setBackground(ColorScheme.DARK_GRAY_COLOR);
        toggle.setForeground(Color.WHITE);
        toggle.addActionListener(event -> onToggle.accept(toggle.isSelected()));
        add(toggle);
        add(status);
        JLabel instructions = new JLabel("<html><br>Use the GPU plugin with this test client.<br><br>"
            + "<b>Developer test build</b><br>World clicks follow the mirror. "
            + "Inventory, chat and minimap stay normal.</html>");
        instructions.setForeground(Color.LIGHT_GRAY);
        add(instructions);
        updateState(false, false, false);
    }

    void updateState(boolean enabled, boolean available, boolean loggedIn)
    {
        toggle.setSelected(enabled);
        String text = !enabled ? "Mirror off" : !available ? "Requires test client and GPU; turn 117 HD off"
            : !loggedIn ? "Log in to test" : "Mirror active";
        status.setText("<html>" + text + "</html>");
        status.setForeground(enabled && !available ? Color.ORANGE : Color.LIGHT_GRAY);
    }

    static BufferedImage createIcon()
    {
        BufferedImage image = new BufferedImage(24, 24, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try
        {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setColor(new Color(235, 191, 92));
            graphics.fillRoundRect(11, 2, 2, 20, 2, 2);
            graphics.fillPolygon(new int[]{1, 8, 8}, new int[]{12, 5, 19}, 3);
            graphics.fillPolygon(new int[]{23, 16, 16}, new int[]{12, 5, 19}, 3);
        }
        finally
        {
            graphics.dispose();
        }
        return image;
    }
}
