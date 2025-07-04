package at.cms.api.enums;

public enum Choices {
    DailyCall(1),
    Comparison(2),
    StatusPerTag(3);

    private final int choiceNumber;

    Choices(int number) {
        this.choiceNumber = number;
    }

    public int getChoiceNumber() {
        return choiceNumber;
    }
}
