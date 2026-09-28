package com.team.tetris.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;

import com.team.tetris.common.Screen;

/** 게임 기록에 등록할 플레이어 이름을 입력받는 화면. */
public class NameInputScreen extends JPanel implements Screen {
	private static final Color BACKGROUND_COLOR = Color.BLACK;
	private static final Color TEXT_COLOR = Color.WHITE;
	private static final Color ACCENT_COLOR = Color.CYAN;
	private static final Color ERROR_COLOR = Color.RED;

	private final ScreenRouter router;
	private final JTextField nameField;
	private final JLabel validationLabel;
	private String submittedName;

	public NameInputScreen(ScreenRouter router) {
		this.router = Objects.requireNonNull(router, "router");

		setLayout(new BorderLayout(16, 16));
		setBackground(BACKGROUND_COLOR);
		setBorder(BorderFactory.createEmptyBorder(32, 24, 24, 24));
		setFocusable(true);
		setOpaque(true);

		JLabel titleLabel = new JLabel("NEW HIGH SCORE", SwingConstants.CENTER);
		titleLabel.setForeground(ACCENT_COLOR);
		titleLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 24));
		add(titleLabel, BorderLayout.NORTH);

		JPanel inputPanel = new JPanel(new GridBagLayout());
		inputPanel.setBackground(BACKGROUND_COLOR);

		JLabel promptLabel = new JLabel("Enter your name");
		promptLabel.setForeground(TEXT_COLOR);
		promptLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 16));

		nameField = new JTextField(16);
		nameField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 18));
		nameField.setForeground(TEXT_COLOR);
		nameField.setCaretColor(ACCENT_COLOR);
		nameField.setBackground(new Color(32, 32, 32));
		nameField.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(ACCENT_COLOR),
				BorderFactory.createEmptyBorder(8, 10, 8, 10)));
		nameField.addActionListener(event -> submitName());

		validationLabel = new JLabel(" ", SwingConstants.CENTER);
		validationLabel.setForeground(ERROR_COLOR);
		validationLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));

		JButton submitButton = new JButton("Submit");
		submitButton.setFont(new Font(Font.MONOSPACED, Font.BOLD, 14));
		submitButton.addActionListener(event -> submitName());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 12, 0);
		inputPanel.add(promptLabel, constraints);

		constraints.gridy++;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 8, 0);
		inputPanel.add(nameField, constraints);

		constraints.gridy++;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 12, 0);
		inputPanel.add(validationLabel, constraints);

		constraints.gridy++;
		constraints.fill = GridBagConstraints.NONE;
		constraints.insets = new Insets(0, 0, 0, 0);
		inputPanel.add(submitButton, constraints);
		add(inputPanel, BorderLayout.CENTER);

		JLabel helpLabel = new JLabel("[ENTER] Submit    [ESC] Back", SwingConstants.CENTER);
		helpLabel.setForeground(Color.GRAY);
		helpLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		add(helpLabel, BorderLayout.SOUTH);

		getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
				KeyStroke.getKeyStroke("ESCAPE"), "backToGameOver");
		getActionMap().put("backToGameOver", new AbstractAction() {
			@Override
			public void actionPerformed(java.awt.event.ActionEvent event) {
				router.showScreen(ScreenRouter.GAME_OVER);
			}
		});
	}

	@Override
	public JPanel getPanel() {
		return this;
	}

	@Override
	public void onShow() {
		submittedName = null;
		nameField.setText("");
		validationLabel.setText(" ");
		nameField.requestFocusInWindow();
	}

	@Override
	public void onHide() {
		// No timers or external resources need cleanup.
	}

	/** 마지막으로 제출한 이름을 반환하거나, 아직 제출하지 않았다면 null을 반환한다. */
	public String getSubmittedName() {
		return submittedName;
	}

	private void submitName() {
		String name = nameField.getText().strip();
		if (name.isEmpty()) {
			validationLabel.setText("Name cannot be blank.");
			nameField.requestFocusInWindow();
			return;
		}

		submittedName = name;
		router.showScreen(ScreenRouter.SCOREBOARD);
	}
}
