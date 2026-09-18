package com.team.tetris;

import com.team.tetris.common.constants.GameConstants;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(GameConstants.GAME_TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(600, 800);
            frame.setLocationRelativeTo(null);
            frame.add(new JLabel("Tetris placeholder", SwingConstants.CENTER));
            // TODO(B): ScreenRouter 완성 후 교체
            frame.setVisible(true);
        });
    }
}