#!/usr/local/bin/Rscript

library(extrafont)
loadfonts()

draw_plot <- function(name, displayName, iguana, antlr) {
	output <- paste(name, ".pdf", sep="")
	pdf(output, width=12, height=6, family="CM Roman")

	boxplot(iguana$Score, antlr$Score, horizontal=TRUE, names = c("Iguana", "ANTLR"), las=2, outline=FALSE, main=displayName, xlab="Running time (milliseconds)")
	text(x = boxplot.stats(round(iguana$Score, 2))$stats, labels = boxplot.stats(round(iguana$Score, 2))$stats, y = 0.55)
	text(x = boxplot.stats(round(antlr$Score, 2))$stats, labels = boxplot.stats(round(antlr$Score, 2))$stats, y = 1.55)

	dev.off()
}

names <- c("RxJava", "elasticsearch", "jdk7u-jdk", "guava", "junit4")
displayNames <- c("RxJava", "Elastic Search", "OpenJDK 7", "Guava", "Junit 4")

total_iguana <- data.frame()
total_antlr <- data.frame()

for (i in 1:length(names)) {
	name <- names[i]
	displayName <- displayNames[i]
	output <- paste(name, ".pdf", sep="")
	pdf(output, width=12, height=8, family="CM Roman")

	iguana <- read.csv(paste("Iguana_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	iguana[, "Score"]  <- as.numeric(iguana[, "Score"])
	total_iguana <- rbind(total_iguana, iguana)

	antlr <- read.csv(paste("Antlr_", name, ".csv", sep=""), header=TRUE, sep=",", dec=",", stringsAsFactors=FALSE)
	antlr[, "Score"]  <- as.numeric(antlr[, "Score"])
	total_antlr <- rbind(total_antlr, antlr)

	draw_plot(name, displayName, iguana, antlr)
}

draw_plot("total", "All Files", total_iguana, total_antlr)

for (name in names) {
	output <- paste(name, ".pdf", sep="")
	embedded_output <- paste("embedded_", output, sep="")
	embed_fonts(output, outfile=embedded_output)
}

embed_fonts("total.pdf", outfile="embedded_total.pdf")
