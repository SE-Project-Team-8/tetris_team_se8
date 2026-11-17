package com.team.tetris.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.font.TextAttribute;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import com.team.tetris.common.Screen;
import com.team.tetris.ui.theme.PillButton;
import com.team.tetris.ui.theme.UiColors;
import com.team.tetris.ui.theme.UiFonts;
import com.team.tetris.ui.theme.UiPainter;

/** 게임 기록에 등록할 플레이어 이름을 입력받는 화면. */
public class NameInputScreen extends JPanel implements Screen {
	private static final Color ERROR_COLOR = UiColors.PLAYER2;

	private final ScreenRouter router;
	private final Consumer<String> onSubmit;
	private final JLabel titleLabel;
	private final JLabel promptLabel;
	private final InputBox inputBox;
	private final JTextField nameField;
	private final JLabel validationLabel;
	private final JLabel hintLabel;
	private final PillButton submitButton;
	private String submittedName;

	public NameInputScreen(ScreenRouter router, Consumer<String> onSubmit) {
		this.router = Objects.requireNonNull(router, "router");
		this.onSubmit = Objects.requireNonNull(onSubmit, "onSubmit");

		setLayout(new BorderLayout());
		setBackground(UiColors.BACKGROUND);
		setFocusable(true);
		setOpaque(true);

		JPanel inputPanel = new JPanel(new GridBagLayout());
		inputPanel.setOpaque(false);

		titleLabel = new JLabel("이름을 입력하세요", SwingConstants.CENTER);
		titleLabel.setForeground(UiColors.TEXT_PRIMARY);

		promptLabel = new JLabel("TOP 10 기록!  스코어보드에 등록됩니다", SwingConstants.CENTER);
		promptLabel.setForeground(UiColors.TEXT_SECONDARY);

		nameField = new JTextField(16);
		nameField.setOpaque(false);
		nameField.setForeground(UiColors.CREAM);
		nameField.setCaretColor(UiColors.ACCENT);
		nameField.setBorder(BorderFactory.createEmptyBorder());
		nameField.addActionListener(event -> submitName());
		nameField.getDocument().addDocumentListener(new DocumentListener() {
			@Override
			public void insertUpdate(DocumentEvent event) { updateNameFont(); }
			@Override
			public void removeUpdate(DocumentEvent event) { updateNameFont(); }
			@Override
			public void changedUpdate(DocumentEvent event) { }
		});
		inputBox = new InputBox(nameField);

		validationLabel = new JLabel(" ", SwingConstants.CENTER);
		validationLabel.setForeground(ERROR_COLOR);

		hintLabel = new JLabel("최대 20자  ·  Enter 확정  ·  Esc 취소", SwingConstants.CENTER);
		hintLabel.setForeground(UiColors.withAlpha(UiColors.TEXT_MUTED, 0.8f));

		submitButton = new PillButton("등록", true);
		submitButton.addActionListener(event -> submitName());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridx = 0;
		constraints.gridy = 0;
		constraints.insets = new Insets(0, 0, 18, 0);
		inputPanel.add(titleLabel, constraints);

		constraints.gridy++;
		constraints.insets = new Insets(0, 0, 28, 0);
		inputPanel.add(promptLabel, constraints);

		constraints.gridy++;
		constraints.insets = new Insets(0, 0, 10, 0);
		inputPanel.add(inputBox, constraints);

		constraints.gridy++;
		constraints.fill = GridBagConstraints.HORIZONTAL;
		constraints.insets = new Insets(0, 0, 4, 0);
		inputPanel.add(validationLabel, constraints);

		constraints.gridy++;
		constraints.insets = new Insets(0, 0, 24, 0);
		inputPanel.add(hintLabel, constraints);

		constraints.gridy++;
		constraints.fill = GridBagConstraints.NONE;
		constraints.insets = new Insets(0, 0, 0, 0);
		inputPanel.add(submitButton, constraints);
		add(inputPanel, BorderLayout.CENTER);

		// 입력창이나 버튼에 포커스가 있어도 ESC로 게임 오버 화면에 돌아갈 수 있게 한다.
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
		// 화면 인스턴스를 다시 사용할 때 이전 입력, 오류 메시지, 제출값이 남지 않게 초기화한다.
		submittedName = null;
		nameField.setText("");
		validationLabel.setText(" ");
		submitButton.setEnabled(true);
		nameField.setEnabled(true);
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

	public void showError(String message) {
		validationLabel.setText(Objects.requireNonNull(message, "message"));
		submitButton.setEnabled(true);
		nameField.setEnabled(true);
		nameField.requestFocusInWindow();
	}

	@Override
	public void doLayout() {
		// 창 크기(S/M/L)가 바뀌면 글자 크기도 창 폭 배율에 맞춘다. 컴포넌트 크기는 각자 getPreferredSize에서 계산한다.
		float scale = scale();
		titleLabel.setFont(UiFonts.display(Math.round(28 * scale)));
		promptLabel.setFont(UiFonts.body(Math.round(14 * scale)));
		updateNameFont();
		validationLabel.setFont(UiFonts.body(Math.round(13 * scale)));
		hintLabel.setFont(UiFonts.body(Math.round(13 * scale)));
		submitButton.setFontSize(Math.round(19 * scale));
		submitButton.setPreferredSize(new Dimension(Math.round(150 * scale), Math.round(50 * scale)));
		super.doLayout();
	}

	// 한글처럼 고정폭 서체에 없는 글자를 입력하면 깨지지 않게 대체 서체로 바꾼다.
	private void updateNameFont() {
		float size = Math.round(30 * scale());
		nameField.setFont(UiFonts.monoOrFallback(nameField.getText(), size)
				.deriveFont(Map.of(TextAttribute.TRACKING, 0.25f)));
	}

	private float scale() {
		return UiPainter.scale(Math.max(1, getWidth()));
	}

	// Enter 키와 제출 버튼이 같은 검증 및 화면 전환 경로를 사용하도록 한다.
	private void submitName() {
		String name = nameField.getText().strip();
		if (name.isEmpty()) {
			validationLabel.setText("이름을 입력해 주세요.");
			nameField.requestFocusInWindow();
			return;
		}
		if (name.codePointCount(0, name.length()) > 20 || name.codePoints().anyMatch(Character::isISOControl)) {
			validationLabel.setText("제어 문자 없이 1~20자로 입력해 주세요.");
			nameField.requestFocusInWindow();
			return;
		}

		submittedName = name;
		submitButton.setEnabled(false);
		nameField.setEnabled(false);
		onSubmit.accept(name);
	}

	/** 입력창을 감싸는 둥근 상자: board 채움 + player1 색 3px 테두리. */
	private final class InputBox extends JPanel {
		InputBox(JTextField field) {
			super(new BorderLayout());
			setOpaque(false);
			add(field, BorderLayout.CENTER);
		}

		@Override
		public Dimension getPreferredSize() {
			float scale = scale();
			return new Dimension(Math.round(340 * scale), Math.round(78 * scale));
		}

		@Override
		public void doLayout() {
			int padding = Math.round(34 * scale());
			setBorder(BorderFactory.createEmptyBorder(0, padding, 0, padding));
			super.doLayout();
		}

		@Override
		protected void paintComponent(Graphics g) {
			Graphics2D g2d = (Graphics2D) g.create();
			try {
				UiPainter.prepare(g2d);
				UiPainter.drawPanel(g2d, 0, 0, getWidth(), getHeight(), Math.round(18 * scale()),
						UiColors.BOARD, UiColors.PLAYER1, 3f);
			} finally {
				g2d.dispose();
			}
		}
	}

}
