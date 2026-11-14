package fr._2gether_asso.survival_scoreboard;

/** A user error in a command: the message is shown to the sender */
final class CommandFailure extends RuntimeException {
	CommandFailure(String message) {
		super(message);
	}
}
