package cz.siret.prank.utils.rlang

import cz.siret.prank.program.params.Parametrized
import cz.siret.prank.utils.Futils
import cz.siret.prank.utils.Sutils
import groovy.transform.CompileStatic

import static cz.siret.prank.utils.Futils.delete

/**
 * produces R code for generating plots
 */
@CompileStatic
class RPlotter implements Parametrized {

    RExecutor rexec = new RExecutor()

    String outdir

    @Deprecated
    String csvfile

    List<String> header

    int size = 1000
    int dpi = 130

    RPlotter(String csvfile, String outdir) {
        this.csvfile = csvfile
        this.outdir = outdir

        header = Sutils.split(new File(csvfile).readLines()[0], ",")
    }

    RPlotter(String outdir) {
        this.outdir = outdir
    }

//    void plot1DAll(int threads) {
//        plot1DVariables(header, threads)
//    }
//
//    @Deprecated
//    @CompileStatic(TypeCheckingMode.SKIP)
//    void plot1DVariables(List<String> variables, int threads) {
//        GParsPool.withPool(threads) {
//            variables.eachParallel {
//                plot1DVariableOld(it as String)
//            }
//        }
//
//        cleanup()
//    }

    String PALETTE = """ c("#1a9850", "#90ce60", "#d9ef8b",  "#ffffbe", "#fee08b", "#fc8c58", "#d73027") """
    //String PALETTE = """ c("#d73027", "#ffffbe", "#1a9850") """
    // String PALETTE = """ c("green4","green3","yellow","gold","red3") """

    void cleanup() {
        delete("$outdir/Rplots.pdf")
    }

    void plot1DVariable(String tablef, String label) {
        tablef = Futils.absSafePath(tablef)

        String rcode = """      
            if (!require("ggplot2")) {
                  install.packages("ggplot2", dependencies = TRUE, repos = "http://cran.us.r-project.org")
                  library(ggplot2)
            }
            library(scales)

            r <- $PALETTE

            data <- read.csv("$tablef",
                  stringsAsFactors = TRUE, 
                  strip.white = TRUE,)

            xx=names(data)[1]
            yy=names(data)[2]

            colnames(data)=c("V1","V2")

            # make V1 an ordered factor to keep order from csv
            data\$V1 <- factor(data\$V1, levels = data\$V1)

            p <- ggplot(data, aes(x=V1, y=V2, colour=V2, fill = V2)) +
                 labs(x = xx, y = yy, colour="") +
                 geom_bar(stat="identity", position = 'dodge', alpha = 1, color="gray20") +
                 geom_text(aes(label = V2), vjust = 1.5, colour = "black") +
                 scale_fill_gradientn(colours=r) +
                 theme(axis.text.x = element_text(angle = 280, hjust = 0), legend.title = element_blank())

            fname <- paste(yy,".png", sep="")
            ggsave(file=fname, dpi=$dpi, limitsize = FALSE)
        """
        // to add line plot: p  +  geom_line(size = 1, color="gray40") + geom_point(shape=18, size=4, color="gray20")

        rexec.runCode(rcode, label, outdir)
    }

    void plot1DVariableHorizontal(String tablef, String label) {
        tablef = Futils.absSafePath(tablef)

        String rcode = """      
            if (!require("ggplot2")) {
                  install.packages("ggplot2", dependencies = TRUE, repos = "http://cran.us.r-project.org")
                  library(ggplot2)
            }
            library(scales)

            r <- $PALETTE

            data <- read.csv("$tablef",
                  stringsAsFactors = TRUE, 
                  strip.white = TRUE,)
                  
            data <- data[seq(dim(data)[1],1),] # reverse row order      

            xx=names(data)[1]
            yy=names(data)[2]

            colnames(data)=c("V1","V2")

            # make V1 an ordered factor to keep order from csv
            data\$V1 <- factor(data\$V1, levels = data\$V1)

            p <- ggplot(data, aes(x=V1, y=V2, colour=V2, fill = V2)) +
                 labs(x = xx, y = yy, colour="") +
                 geom_bar(stat="identity", position = 'dodge', alpha = 1, color="gray20") +
                 geom_text(aes(label = V2), hjust = 1, position = position_dodge(1), colour = "black") +
                 scale_fill_gradientn(colours=r) +
                 theme(legend.title = element_blank(), axis.text=element_text(color="black")) +
                 coord_flip()
                 
            nrows <- nrow(data)  # scale with rows
            hh <- max(5, 0.57*nrows)    
            
            labels <- sapply(data\$V1, as.character)
            max_label_chars = max(nchar(labels))
            ww <- max(20, 15 + 0.2*max_label_chars)  # scale with longest label lenght

            fname <- paste(yy,".png", sep="")
            ggsave(file=fname, dpi=$dpi, width = ww, height = hh, units = "cm", limitsize = FALSE)
        """
        // to add line plot: p  +  geom_line(size = 1, color="gray40") + geom_point(shape=18, size=4, color="gray20")

        rexec.runCode(rcode, label, outdir)
    }

//    @Deprecated
//    void plot1DVariableOld(String name) {
//
//        int column = header.indexOf(name)
//
//        String tabf = "../"+Futils.shortName(csvfile) //FileUtils.relativize(csvfile, outdir)
//
//        String rcode = """
//            if (!require("ggplot2")) {
//                  install.packages("ggplot2", dependencies = TRUE, repos = "http://cran.us.r-project.org")
//                  library(ggplot2)
//            }
//            library(scales)
//
//            r <- c("green4","green3","yellow","gold","red3")
//
//            data <- read.csv("$tabf")
//
//            xx = names(data)[1]
//            yy = names(data)[${column+1}]
//
//            p <- ggplot(data, aes_string(x=xx, y=yy, colour=yy, fill = yy))
//
//            p + geom_bar(stat="identity", position = 'dodge', alpha = 3/4, color="gray20") + scale_fill_gradientn(colours=r) + theme(axis.text.x = element_text(angle = 340, hjust = 0))
//
//            fname <- paste(yy,".png", sep="")
//            ggsave(file=fname, dpi=$dpi)
//        """
//        // to add line plot: p  +  geom_line(size = 1, color="gray40") + geom_point(shape=18, size=4, color="gray20")
//
//        rexec.runCode(rcode, name, outdir)
//    }

    /**
     *
     * @param tablef  2d csv table
     */
    void plotHeatMapTable(String tablef, String label, String xlab, String ylab) {
        tablef = Futils.absSafePath(tablef)

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
                cnames <- sub("^X", "", cnames)               # remove X prefix added by colnames
                mat_data <- data.matrix(data[,2:ncol(data)])  # transform column 2-5 into a matrix

                rownames(mat_data) <- rnames                  # assign row names
                colnames(mat_data) <- cnames

                #########################################################
                ### C) Customizing and plotting the heat map
                #########################################################

                # creates a own color palette from red to green
                my_palette <- colorRampPalette($PALETTE)(n = 20000)

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
                          notecex=1,            # cell note font size multiplier
                          cex.lab=1,
                          cex.axis=1, 
                          #srtCol=15,           # rotate x captions
                          key=FALSE,            # don't render color legend
                          density.info="none",  # turns off density plot inside color legend
                          trace="none",         # turns off trace lines inside the heat map
                          margins=c(10,10),     # widens margins around plot
                          lhei=c(0.1,1),
                          lwid=c(0.1,1),
                          col=my_palette,       # use on color palette defined earlier
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
