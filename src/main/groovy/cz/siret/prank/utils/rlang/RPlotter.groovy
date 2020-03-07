package cz.siret.prank.utils.rlang

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovyx.gpars.GParsPool

import static cz.siret.prank.utils.Futils.delete

/**
 * produces R code for generating plots
 */
class RPlotter implements Parametrized {

    RExecutor rexec = new RExecutor()

    String outdir
    String csvfile

    List<String> header

    int size = 1000
    int dpi = 150

    RPlotter(String csvfile, String outdir) {
        this.csvfile = csvfile
        this.outdir = outdir

        header = Sutils.split(new File(csvfile).readLines()[0], ",")
    }

    RPlotter(String outdir) {
        this.outdir = outdir
    }

    void plot1DAll(int threads) {
        plot1DVariables(header, threads)
    }

    void plot1DVariables(List<String> variables, int threads) {
        GParsPool.withPool(threads) {
            variables.eachParallel {
                plot1DVariable(it)
            }
        }

        cleanup()
    }

    void cleanup() {
        delete("$outdir/Rplots.pdf")
    }

    void plot1DVariable(String name) {

        int column = header.indexOf(name)

        String tabf = "../"+Futils.shortName(csvfile) //FileUtils.relativize(csvfile, outdir)

        String rcode = """
            if (!require("ggplot2")) {
                  install.packages("ggplot2", dependencies = TRUE, repos = "http://cran.us.r-project.org")
                  library(ggplot2)
            }
            library(scales)

            r <- c("green4","green3","yellow","gold","red3")

            data <- read.csv("$tabf")

            xx = names(data)[1]
            yy = names(data)[${column+1}]

            p <- ggplot(data, aes_string(x=xx, y=yy, colour=yy, fill = yy))

            p + geom_bar(stat="identity", position = 'dodge', alpha = 3/4, color="gray20") + scale_fill_gradientn(colours=r) + theme(axis.text.x = element_text(angle = 340, hjust = 0))

            ggsave(file=paste(yy,".png"), dpi=$dpi)
        """
        //     +  geom_line(size = 1, color="gray40")  + geom_point(shape=18, size=4, color="gray20")
        // dpi=150

        rexec.runCode(rcode, name, outdir)

    }

    /**
     *
     * @param tablef  2d csv table
     */
    void plotHeatMapTable(String tablef, String label, String xlab, String ylab) {

        String rcode = """
                #########################################################
                ### A) Installing and loading required packages
                #########################################################

                if (!require("gplots")) {
                  install.packages("gplots", dependencies = TRUE)
                  library(gplots)
                }
                if (!require("RColorBrewer")) {
                  install.packages("RColorBrewer", dependencies = TRUE)
                  library(RColorBrewer)
                }


                #########################################################
                ### B) Reading in data and transform it into matrix format
                #########################################################

                data <- read.csv("$tablef", comment.char="#")
                rnames <- data[,1]                            # assign labels in column 1 to "rnames"
                cnames <- colnames(data)
                cnames <- cnames[2:length(cnames)]
                mat_data <- data.matrix(data[,2:ncol(data)])  # transform column 2-5 into a matrix

                rownames(mat_data) <- rnames                  # assign row names
                colnames(mat_data) <- cnames

                #########################################################
                ### C) Customizing and plotting the heat map
                #########################################################

                # creates a own color palette from red to green
                my_palette <- colorRampPalette(c("green4", "gold", "red3"))(n = 20000)

                # (optional) defines the color breaks manually for a "skewed" color transition
                #col_breaks = c(seq(-1,0,length=100),  # for red
                #seq(0,0.8,length=100),              # for yellow
                #               seq(0.8,1,length=100))              # for green

                # creates a 5 x 5 inch image
                png("${label}.png",    # create PNG for the heat map
                    width = 10*300,        # 5 x 300 pixels
                    height = 5*300,
                    res = 300,            # 300 pixels per inch
                    pointsize = 5)        # smaller font size


                #par(cex.main=1.5) # increase ception font size
                heatmap.2(mat_data,
                          main = "$label",      # heat map title
                          cellnote = mat_data,  # same data set for cell labels
                          notecol="black",      # change font color of cell labels to black
                          notecex=0.7,          # cell note font size multiplier 
                          density.info="none",  # turns off density plot inside color legend
                          trace="none",         # turns off trace lines inside the heat map
                          # margins =c(8,8),    # widens margins around plot
                          col=my_palette,       # use on color palette defined earlier
                          #breaks=col_breaks,   # enable color transition at specified limits
                          dendrogram="none",    # only draw a row dendrogram
                          xlab="$xlab",
                          ylab="$ylab",
                          Rowv=FALSE,           # turn off row clustering
                          Colv=FALSE)           # turn off column clustering

                dev.off()               # close the PNG device
        """

        rexec.runCode(rcode, label, outdir)
    }

}
