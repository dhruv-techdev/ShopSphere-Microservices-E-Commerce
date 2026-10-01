"""Pure pandas co-purchase ("frequently bought together") computation. No I/O."""

import pandas as pd

LINE_COLUMNS = ["order_id", "product_id"]
RESULT_DTYPES = {
    "product_id": "int64",
    "recommended_product_id": "int64",
    "co_purchase_count": "int64",
    "confidence": "float64",
    "lift": "float64",
    "rank": "int64",
}


def build_co_purchase_matrix(lines: pd.DataFrame, top_n: int, min_co_purchases: int = 1) -> pd.DataFrame:
    """
    Turns order lines into the top-N co-purchased products per product.

    ``lines`` has one row per order line (``order_id``, ``product_id``); quantities and repeated
    lines of the same product in one order count once. For every ordered pair (A, B):

    - ``co_purchase_count``: orders containing both A and B
    - ``confidence``: P(B | A) = co_purchase_count / orders containing A
    - ``lift``: confidence / P(B) — above 1 means "bought together more often than chance"

    Rows are ranked per product by count, then lift, then product id (deterministic ties).
    """
    if top_n < 1 or min_co_purchases < 1:
        raise ValueError("top_n and min_co_purchases must be >= 1")

    baskets = lines.loc[:, LINE_COLUMNS].drop_duplicates()
    if baskets.empty:
        return _empty()

    total_orders = baskets["order_id"].nunique()
    orders_per_product = baskets.groupby("product_id")["order_id"].nunique()

    multi_item = baskets[baskets.groupby("order_id")["product_id"].transform("size") > 1]
    pairs = multi_item.merge(multi_item, on="order_id", suffixes=("", "_other"))
    pairs = pairs[pairs["product_id"] != pairs["product_id_other"]]
    if pairs.empty:
        return _empty()

    counts = (
        pairs.groupby(["product_id", "product_id_other"], as_index=False)
        .size()
        .rename(columns={"product_id_other": "recommended_product_id", "size": "co_purchase_count"})
    )
    counts = counts.loc[counts["co_purchase_count"] >= min_co_purchases].copy()
    if counts.empty:
        return _empty()

    support_a = counts["product_id"].map(orders_per_product)
    support_b = counts["recommended_product_id"].map(orders_per_product)
    counts["confidence"] = counts["co_purchase_count"] / support_a
    counts["lift"] = counts["confidence"] / (support_b / total_orders)

    ranked = counts.sort_values(
        ["product_id", "co_purchase_count", "lift", "recommended_product_id"],
        ascending=[True, False, False, True],
    )
    ranked["rank"] = ranked.groupby("product_id").cumcount() + 1
    top = ranked[ranked["rank"] <= top_n]

    return top.loc[:, list(RESULT_DTYPES)].astype(RESULT_DTYPES).reset_index(drop=True)


def _empty() -> pd.DataFrame:
    return pd.DataFrame({column: pd.Series(dtype=dtype) for column, dtype in RESULT_DTYPES.items()})
