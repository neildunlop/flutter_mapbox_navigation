import UIKit
import MapboxNavigation

protocol CustomBottomBannerViewDelegate: AnyObject {
    func customBottomBannerDidCancel(_ banner: CustomBottomBannerView)
}

class CustomBottomBannerView: UIView {

    @IBOutlet var contentView: UIView!
    @IBOutlet weak var etaLabel: UILabel!
    @IBOutlet weak var progressBar: UIProgressView!
    @IBOutlet weak var cancelButton: UIButton!
    
    var progress: Float {
        get {
            return progressBar.progress
        }
        set {
            progressBar.setProgress(newValue, animated: false)
        }
    }
    
    var eta: String? {
        get {
            return etaLabel.text
        }
        set {
            etaLabel.text = newValue
        }
    }
    
    weak var delegate: CustomBottomBannerViewDelegate?
    
    private func initFromNib() {
        
//        let bundle = Bundle.init(for: CustomBottomBannerView.self)
//        if let viewToAdd = bundle.loadNibNamed("CustomBottomBannerView", owner: self, options: nil),
//           let contentView = viewToAdd.first as? UIView {
//            addSubview(contentView)
//            contentView.frame = self.bounds
//            contentView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
//        }
            
        
        
        Bundle.main.loadNibNamed(String(describing: CustomBottomBannerView.self),
                                 owner: self,
                                 options: nil)
        addSubview(contentView)
        contentView.frame = bounds
        contentView.autoresizingMask = [.flexibleWidth, .flexibleHeight]

        progressBar.progressTintColor = .systemGreen
        progressBar.layer.borderColor = UIColor.black.cgColor
        progressBar.layer.borderWidth = 2
        progressBar.layer.cornerRadius = 5

        cancelButton.backgroundColor = .systemGray
        cancelButton.layer.cornerRadius = 5
        cancelButton.setTitleColor(.darkGray, for: .highlighted)

        backgroundColor = UIColor.black.withAlphaComponent(0.3)
        layer.cornerRadius = 10
    }
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        initFromNib()
    }
    
    required init?(coder: NSCoder) {
        super.init(coder: coder)
        initFromNib()
    }
    
    @IBAction func onCancel(_ sender: Any) {
        delegate?.customBottomBannerDidCancel(self)
    }
}
