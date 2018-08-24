#!/usr/local/bin/Rscript

iguana <- read.csv("Iguana.csv", header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
iguana[, "Score"]  <- as.numeric(iguana[, "Score"])

antlr <- read.csv("Antlr.csv", header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
antlr[, "Score"]  <- as.numeric(antlr[, "Score"])


pdf("plot.pdf", width=12, height=8)

boxplot(iguana$Score, antlr$Score, horizontal=TRUE, names = c("Iguana", "Antlr"), las=2, outline=FALSE)
text(x = boxplot.stats(round(iguana$Score, 2))$stats, labels = boxplot.stats(round(iguana$Score, 2))$stats, y = 0.55)
text(x = boxplot.stats(round(antlr$Score, 2))$stats, labels = boxplot.stats(round(antlr$Score, 2))$stats, y = 1.55)

dev.off()
