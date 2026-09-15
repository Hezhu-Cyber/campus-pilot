from evals.runner import evaluate_offline, load_cases


def test_offline_evaluation_suite_passes() -> None:
    report = evaluate_offline(load_cases())
    assert report["pass_rate"] == 1.0
    assert report["failures"] == []
