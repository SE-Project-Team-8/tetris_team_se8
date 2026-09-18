package com.team.tetris.common;

import javax.swing.JPanel;

/** All screens implement this contract and are registered with ScreenRouter. */
public interface Screen {

    JPanel getPanel();

    void onShow();

    void onHide();
}