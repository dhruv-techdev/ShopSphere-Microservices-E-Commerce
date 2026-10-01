import pandas as pd
import pytest

from app.matrix import RESULT_DTYPES, build_co_purchase_matrix


def lines(*orders: list[int]) -> pd.DataFrame:
    """lines([1, 2], [1, 3]) → order 1 has products 1 and 2, order 2 has products 1 and 3."""
    rows = [(order_id, product_id) for order_id, products in enumerate(orders, start=1) for product_id in products]
    return pd.DataFrame(rows, columns=["order_id", "product_id"])


def recommended(matrix: pd.DataFrame, product_id: int) -> list[int]:
    return matrix.loc[matrix["product_id"] == product_id, "recommended_product_id"].tolist()


def row(matrix: pd.DataFrame, product_id: int, other: int) -> pd.Series:
    match = matrix[(matrix["product_id"] == product_id) & (matrix["recommended_product_id"] == other)]
    assert len(match) == 1
    return match.iloc[0]


def test_ranks_by_how_often_products_are_bought_together():
    matrix = build_co_purchase_matrix(lines([1, 2], [1, 2], [1, 3], [2, 3]), top_n=10)

    assert recommended(matrix, 1) == [2, 3]
    assert row(matrix, 1, 2)["co_purchase_count"] == 2
    assert row(matrix, 1, 3)["co_purchase_count"] == 1
    assert matrix.loc[matrix["product_id"] == 1, "rank"].tolist() == [1, 2]


def test_counts_are_symmetric_but_confidence_is_directional():
    # Product 1 is in 3 orders, product 2 in 1; they share one.
    matrix = build_co_purchase_matrix(lines([1, 2], [1, 3], [1, 4]), top_n=10)

    assert row(matrix, 1, 2)["co_purchase_count"] == row(matrix, 2, 1)["co_purchase_count"] == 1
    assert row(matrix, 1, 2)["confidence"] == pytest.approx(1 / 3)
    assert row(matrix, 2, 1)["confidence"] == pytest.approx(1.0)


def test_lift_compares_against_chance():
    # 4 orders; product 2 appears in 2 of them (P=0.5) and in 1 of product 1's 2 orders (conf=0.5) → lift 1.
    matrix = build_co_purchase_matrix(lines([1, 2], [1, 3], [2, 4], [5, 6]), top_n=10)

    assert row(matrix, 1, 2)["lift"] == pytest.approx(1.0)
    assert row(matrix, 5, 6)["lift"] == pytest.approx(4.0)


def test_ties_break_on_lift_then_product_id():
    # Product 1 shares one order each with 2, 3 and 4; product 4 is rarer → higher lift.
    matrix = build_co_purchase_matrix(lines([1, 2, 3, 4], [2, 5], [3, 5]), top_n=10)

    assert recommended(matrix, 1) == [4, 2, 3]


def test_repeated_lines_and_quantities_count_once_per_order():
    matrix = build_co_purchase_matrix(lines([1, 1, 2, 2]), top_n=10)

    assert row(matrix, 1, 2)["co_purchase_count"] == 1
    assert recommended(matrix, 1) == [2]


def test_single_item_orders_produce_no_pairs():
    matrix = build_co_purchase_matrix(lines([1], [2], [3]), top_n=10)

    assert matrix.empty
    assert dict(matrix.dtypes.astype(str)) == RESULT_DTYPES


def test_keeps_only_top_n_per_product():
    matrix = build_co_purchase_matrix(lines([1, 2, 3, 4, 5], [1, 2], [1, 2], [1, 3]), top_n=2)

    assert recommended(matrix, 1) == [2, 3]
    assert matrix.groupby("product_id").size().max() == 2


def test_drops_pairs_below_the_minimum():
    matrix = build_co_purchase_matrix(lines([1, 2], [1, 2], [1, 3]), top_n=10, min_co_purchases=2)

    assert recommended(matrix, 1) == [2]
    assert recommended(matrix, 3) == []


def test_empty_history():
    matrix = build_co_purchase_matrix(pd.DataFrame(columns=["order_id", "product_id"]), top_n=5)

    assert matrix.empty
    assert list(matrix.columns) == list(RESULT_DTYPES)


def test_never_recommends_a_product_for_itself():
    matrix = build_co_purchase_matrix(lines([1, 2], [1, 2, 3]), top_n=10)

    assert not (matrix["product_id"] == matrix["recommended_product_id"]).any()


@pytest.mark.parametrize("top_n, minimum", [(0, 1), (5, 0)])
def test_rejects_invalid_parameters(top_n, minimum):
    with pytest.raises(ValueError):
        build_co_purchase_matrix(lines([1, 2]), top_n=top_n, min_co_purchases=minimum)
