import numpy as np
import csv
import sys

if len(sys.argv) != 4:
    print("useage: python {} explanations.tsv explainable_predicate.txt k".format(sys.argv[0]))
    sys.exit()

exp_fn = sys.argv[1]
pred_fn = sys.argv[2]

pred_exp = dict()
preds = dict()
pred_exp["yes"] = set([])
pred_exp["no"] = set([])

with open(exp_fn) as exp_f, open(pred_fn) as pred_f:
    exp_csv_f = csv.reader(exp_f, delimiter='\t')
    pred_csv_f = csv.reader(pred_f, delimiter=':')
    for row in exp_csv_f:
        preds[row[0]] = row[1:]
    for row in pred_csv_f:
        pred_exp[row[1].lower()].add(row[0].upper())
max_at = int(sys.argv[3])
explainable_at = np.zeros(max_at)
total_at = np.zeros(max_at)
for k in preds:
    for at in range(max_at):
        if len(preds[k]) <= at:
            continue
        explainable = [True if x in preds[k][at] else False for x in pred_exp["yes"]]
        explainable = int(np.sum(explainable) > 0)
        explainable_at[at] += explainable
        total_at[at] += 1

explainable_at = np.cumsum(explainable_at)
total_at = np.cumsum(total_at)

print("Explainable at : {}".format(explainable_at/total_at))
